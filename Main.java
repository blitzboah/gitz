import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Arrays;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        String command = "";

        if(args.length == 0){
            System.out.println("try using <gitz init> if its not a gitz dir");
            return;
        }

        GitRepository gitRepository = new GitRepository();

        try {
            command = args[0];

            switch (command) {
                case "init" -> {
                    gitRepository.gitInit();
                }
                case "cat-file" -> {
                    String[] catFileArgs = argsMaker(args);
                    cmdCatFile(catFileArgs);
                }
                case "hash-object" -> {
                    String[] hashArgs = argsMaker(args);
                    if (hashArgs.length < 3) {
                        System.out.println("usage: hash-object <-w> <-t TAG> <FILE>\n" +
                                "<-w> {write flag}\n" +
                                "<-t TYPE> {-t tag flag TYPE -> specify the type choices" +
                                "=['blob', 'commit', 'tag', 'tree'] default is blob" +
                                "<FILE> read object from file");
                    }
                    cmdHashObject(hashArgs);
                }
                case "ls-tree" -> {
                    String[] lsTreeArgs = argsMaker(args);
                    cmdLsTree(lsTreeArgs);
                }
                case "checkout" -> {
                    String[] checkoutArgs = argsMaker(args);
                    if (checkoutArgs.length < 2) {
                        System.out.println("usage: checkout <commit-hash> <path>");
                        return;
                    }
                    cmdCheckout(checkoutArgs);
                }
                case "show-ref" -> {
                    cmdShowRef();
                }
                case "tag" -> {
                    String[] tagArgs = argsMaker(args);
                    cmdTag(tagArgs);
                }
                case "rev-parse" -> {
                    String[] revParseArgs = argsMaker(args);
                    if (revParseArgs.length < 1) {
                        System.out.println("usage: rev-parse <name> [-t type]");
                        return;
                    }
                    cmdRevParse(revParseArgs);
                }
                case "log" -> {
                    GitLog.cmdLog();
                }
                case "ls-files" -> {
                    boolean verbose = args.length > 1 && args[1].equals("-v");
                    cmdLsFiles(verbose);
                }
                case "check-ignore" -> {
                    String[] ignoreArgs = argsMaker(args);
                    cmdCheckIgnore(ignoreArgs);
                }
                case "status" -> {
                    cmdStatus();
                }
                default -> System.out.println("type correctly lil bro");
            }
        }
        catch (ArrayIndexOutOfBoundsException e){
            System.out.println("no args found");
        }
    }

    public static String[] argsMaker(String[] args){
        String[] newArgs = new String[args.length-1];
        System.arraycopy(args, 1, newArgs, 0, newArgs.length);

        return newArgs;
    }

    public static void cmdCatFile(String[] args) throws Exception{
        if (args.length < 2){
            System.out.println("usage: cat-file <object> [type]");
            return;
        }

        String obj = args[0];
        String type = args[1];

        Path repo = GitRepository.repoFind(".", true);

        catFile(repo, obj, type);
    }

    public static void catFile(Path path, String obj, String type) throws Exception {
        String sha = obj.length() == 40 ? obj : GitRepository.objectFind(path, obj, type, true);

        GitObject object = GitRepository.objectRead(sha);

        if(object != null){
            System.out.println(Arrays.toString(object.serialize()));
        }
        else {
            System.out.println("object not found");
        }
    }

    public static void cmdHashObject(String[] args) throws Exception {
        boolean writeFlag;
        writeFlag = args[0].equals("-w");
        String typeFlag = args[1];
        String type = args[2];
        String filePath = args[3];
        Path repo = GitRepository.repoFind(".", true);

        String sha = GitRepository.objectHash(filePath, type, repo);

        System.out.println(sha);
    }

    public static void cmdLsTree(String[] args) throws Exception {
        Path repo = GitRepository.repoFind(".", true);
        String treeRef = args[0];
        boolean recursive = args.length > 1 && args[1].equals("-r");

        GitTreeLeaf.lsTree(repo, treeRef, recursive, "");
    }

    public static void cmdCheckout(String[] args) throws Exception {
        String commitHash = args[0];
        String targetPath = args[1];

        Path repo = GitRepository.repoFind(".", true);

        String objSha = GitRepository.objectFind(repo, commitHash, null, false);
        GitObject obj = GitRepository.objectRead(objSha);

        if(!(obj instanceof GitCommit)){
            GitCommit commit = (GitCommit) obj;

            byte[] treeHash = commit.getKvlm().get("tree".getBytes(StandardCharsets.UTF_8));
            if(treeHash == null){
                throw new Exception("commit has not tree");
            }

            obj = GitRepository.objectRead(new String(treeHash, StandardCharsets.UTF_8));
        }

        File targetDir = new File(targetPath);
        GitUtils.verifyTargetDirectory(targetDir);

        String realPath = targetDir.getCanonicalPath();
        treeCheckout(repo, (GitTree) obj, Path.of(realPath));
    }

    public static void treeCheckout(Path repo, GitTree tree, Path path) throws Exception{
        for(GitTreeLeaf item : tree.getItems()){
            GitObject obj = GitRepository.objectRead(item.getSha());

            if(obj == null){
                throw new Exception("could not read object "+ item.getSha());
            }

            Path dest = path.resolve(item.getPath().toString());

            if(obj instanceof GitTree){
                Files.createDirectory(dest);
                treeCheckout(repo, (GitTree) obj, dest);
            }
            else if(obj instanceof  GitBlob){
                try(FileOutputStream fos = new FileOutputStream(dest.toFile())){
                    fos.write(obj.serialize());
                }
            }
        }
    }

    public static void cmdShowRef() throws IOException {
        Path repo = GitRepository.repoFind(".", true);
        Map<String, Object> refs = GitUtils.refList(repo, null);
        GitUtils.showRef(repo, refs, true, "refs");
    }

    public static void cmdTag(String[] args) throws IOException {
        Path repo = GitRepository.repoFind(".", true);

        if(args[1] != null){
            String name = args[1];
            String object = args.length > 2 ? args[2] : "HEAD";
            boolean createTagObject = args.length > 3 && args[3].equals("-a");


        }
        else {
            Map<String, Object> refs = GitUtils.refList(repo, repo.resolve(".gitz/refs/tags"));
            GitUtils.showRef(repo, refs, false, "tags");
        }
    }

    public static void cmdRevParse(String[] args) throws Exception {
        String name = args[0];
        String type = null;

        if(args.length > 2 && args[1].equals("-t")){
            type = args[2];
        }

        Path repo = GitRepository.repoFind(".", true);

        String res = GitRepository.objectFind(repo, name, type, true);
        System.out.println(res);
    }

    public static void cmdLsFiles(boolean verbose) throws Exception {
        Path repo = GitRepository.repoFind(".", true);
        assert repo != null;
        GitIndex index = GitIndex.readFromFile(repo.resolve(".gitz/index").toFile());

        if(verbose){
            System.out.println("index file format v" + index.getVersion() + ", containing " + index.getEntries().size() + " entries");
        }

        for(GitIndexEntry e : index.getEntries()) {
            System.out.println(e.getName());

            if (verbose) {
                String entryType = switch (e.getModeType()) {
                    case 0b1000 -> "regular file";
                    case 0b1010 -> "symlink";
                    case 0b1110 -> "git link";
                    default -> "unknown";
                };

                System.out.println("  " + entryType + " with perms: " + Integer.toOctalString(e.getModePerms()));
                System.out.println("  on blob: " + e.getSha());
                System.out.println("  created: " + e.getCtime()[0] + "." + e.getCtime()[1] +
                        ", modified: " + e.getMtime()[0] + "." + e.getMtime()[1]);
                System.out.println("  device: " + e.getDev() + ", inode: " + e.getIno());

                // unix user and group lookup
                String user = "unknown";
                String group = "unknown";
                try {
                    UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
                    UserPrincipal userPrincipal = lookupService.lookupPrincipalByGroupName(String.valueOf(e.getUid()));
                    GroupPrincipal groupPrincipal = lookupService.lookupPrincipalByGroupName(String.valueOf(e.getGid()));

                    user = userPrincipal.getName();
                    group = groupPrincipal.getName();
                    
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                System.out.println("  user: " + user + " (" + e.getUid() + ")  group: " + group + " (" + e.getGid() + ")");
                System.out.println("  flags: stage=" + e.getFlagStage() + " assume_valid=" + e.isFlagAssumeValid());
            }
        }
    }

    public static void cmdCheckIgnore(String[] args) throws Exception {
        Path repo = GitRepository.repoFind(".", true);
        assert repo != null;
        GitIgnore rules = GitIgnore.gitIgnoreRead(repo);

        for(String path: args){
            if(GitIgnore.checkIgnore(rules, path)){
                System.out.println(path);
            }
        }
    }

    public static void cmdStatus() throws Exception {
        Path repo = GitRepository.repoFind(".", true);
        assert repo != null;
        GitIndex index = GitIndex.readFromFile(repo.resolve(".gitz/index").toFile());

        GitStatus.cmdStatusBranch(repo);
        GitStatus.cmdStatusHeadIndex(repo, index);
        System.out.println();
        GitStatus.cmdStatusIndexWorktree(repo, index);
    }

}
