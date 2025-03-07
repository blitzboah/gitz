import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
                case "rm" -> {
                    String[] rmArgs = argsMaker(args);
                    cmdRm(rmArgs);
                }
                case "add" -> {
                    String[] addArgs = argsMaker(args);
                    cmdAdd(addArgs);
                }
                case "show-index" -> {
                    cmdShowIndex();
                }
                case "dump-index" -> {
                    cmdDumpIndex();
                }
                case "commit" -> {
                    String[] commitArgs = argsMaker(args);
                    cmdCommit(commitArgs);
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
            System.out.println(new String(object.serialize(), StandardCharsets.UTF_8));
        }
        else {
            System.out.println("object not found");
        }
    }

    public static void cmdHashObject(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: hash-object [-w] -t <type> <file>");
            return;
        }

        boolean writeFlag = false;
        String type = "blob";  // Default type
        String filePath = null;

        // Process arguments dynamically
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-w" -> writeFlag = true;
                case "-t" -> {
                    if (i + 1 < args.length) {
                        type = args[++i];
                    } else {
                        System.out.println("error: missing type argument after -t");
                        return;
                    }
                }
                default -> filePath = args[i];
            }
        }

        if (filePath == null) {
            System.out.println("error: missing file path");
            return;
        }

        Path repo = GitRepository.repoFind(".", true);
        String sha = GitRepository.objectHash(filePath, type, repo);
        System.out.println(sha);
    }


    public static void cmdLsTree(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("usage: ls-tree <tree-hash> or HEAD");
            return;
        }

        Path repo = GitRepository.repoFind(".", true);
        if (repo == null) {
            System.out.println("error: not a gitz directory");
            return;
        }

        String treeRef = args[0];
        boolean recursive = args.length > 1 && args[1].equals("-r");

        GitTreeLeaf.lsTree(repo, treeRef, recursive, "");
    }


    public static void cmdCheckout(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: checkout <commit-hash> <path>");
            return;
        }

        String commitHash = args[0];
        String targetPath = args[1];

        Path repo = GitRepository.repoFind(".", true);
        if (repo == null) {
            System.out.println("error: not inside a valid gitz repository");
            return;
        }

        String objSha = GitRepository.objectFind(repo, commitHash, null, false);
        if (objSha == null) {
            System.out.println("error: commit hash not found");
            return;
        }

        GitObject obj = GitRepository.objectRead(objSha);
        if (!(obj instanceof GitCommit)) {
            System.out.println("error: provided hash is not a valid commit");
            return;
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

    public static void cmdTag(String[] args) throws Exception {
        Path repo = GitRepository.repoFind(".", true);

        if (args.length > 0) {
            String tagName = args[0];
            String targetCommit = args.length > 1 ? args[1] : "HEAD";
            boolean annotated = args.length > 2 && args[2].equals("-a");

            String commitHash = GitRepository.objectFind(repo, targetCommit, "commit", true);
            if (commitHash == null) {
                System.out.println("commit not found.");
                return;
            }

            Path tagPath = repo.resolve(".gitz/refs/tags/" + tagName);
            Files.writeString(tagPath, commitHash);

            System.out.println("tag " + tagName + " created for commit " + commitHash);
        } else {
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

        // read the HEAD file
        String headContent = Files.readString(repo.resolve(".gitz/HEAD"), StandardCharsets.UTF_8).trim();
        boolean noCommitsYet = false;

        if (headContent.startsWith("ref: refs/heads/")) {
            String branchName = headContent.substring(16);
            Path branchPath = repo.resolve(".gitz/refs/heads/" + branchName);

            if (!Files.exists(branchPath)) {
                // no commits yet because refs/heads/master does not exist
                System.out.println("on branch " + branchName + "\n");
                System.out.println("no commits yet\n");
                noCommitsYet = true;
            } else {
                System.out.println("on branch " + branchName);
            }
        } else {
            System.out.println("not currently on any branch");
        }

        // always check the index (even if no commits exist)
        GitIndex index = GitIndex.readFromFile(repo.resolve(".gitz/index").toFile());

        // show staged files
        if (!noCommitsYet) {
            GitStatus.cmdStatusHeadIndex(repo, index);
        } else {
            System.out.println("changes to be committed:");
            for (GitIndexEntry entry : index.getEntries()) {
                System.out.println("  added: " + entry.getName());
            }
        }

        System.out.println();

        // show untracked files
        GitStatus.cmdStatusIndexWorktree(repo, index);
    }

    public static void cmdRm(String[] args) throws Exception {
        Path repo = GitRepository.repoFind(".", true);
        if (repo == null) {
            System.out.println("error: not a gitz directory");
            return;
        }

        if (args.length == 0) {
            System.out.println("usage: rm <file>");
            return;
        }

        GitIndex index = GitIndex.readFromFile(repo.resolve(".gitz/index").toFile());
        List<Path> paths = new ArrayList<>();

        for (String arg : args) {
            Path filePath = repo.resolve(arg).normalize();
            boolean found = false;
            for (GitIndexEntry entry : index.getEntries()) {
                if (entry.getName().equals(arg)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.println("error: file is not tracked: " + arg);
                return;
            }
            paths.add(filePath);
        }

        GitRepository.rm(repo, paths, true, false);
    }


    public static void cmdAdd(String[] args) throws Exception {
        Path repo = GitRepository.repoFind(".", true);
        if (repo == null) {
            System.out.println("error: not a gitz directory");
            return;
        }

        if (args.length == 0) {
            System.out.println("usage: add <file>");
            return;
        }

        List<Path> paths = new ArrayList<>();
        for (String arg : args) {
            Path path = repo.resolve(arg);
            if (!Files.exists(path)) {
                System.out.println("error: file does not exist: " + arg);
                return;
            }
            paths.add(path);
        }

        GitRepository.add(repo, paths, false, true);
    }


    public static void cmdShowIndex() throws Exception {
        Path repo = GitRepository.repoFind(".", true);
        assert repo != null;
        GitIndex index = GitIndex.readFromFile(repo.resolve(".gitz/index").toFile());

        System.out.println("index file version: "+index.getVersion());
        System.out.println("entries:");

        for(GitIndexEntry e: index.getEntries()){
            System.out.println("  file: " +e.getName());
            System.out.println("  sha-1: "+e.getSha());
            System.out.println("  size:" +e.getFsize());
            System.out.println("  mode:" +Integer.toOctalString(e.getModePerms()));
            System.out.println("------------------------");
        }

    }

    public static void cmdDumpIndex() throws IOException {
        Path repo = GitRepository.repoFind(".", true);
        assert repo != null;
        Path indexFile = repo.resolve(".gitz/index");

        if (!Files.exists(indexFile)) {
            System.out.println("No index file found.");
            return;
        }

        byte[] data = Files.readAllBytes(indexFile);
        System.out.println("Raw index file (hex dump):");

        for (int i = 0; i < data.length; i++) {
            System.out.printf("%02x ", data[i]);
            if ((i + 1) % 16 == 0) System.out.println(); // new line every 16 bytes
        }
        System.out.println();
    }

    public static void cmdCommit(String[] args) throws Exception {
        Path repo = GitRepository.repoFind(".", true);
        if (repo == null) {
            System.out.println("error: not inside a valid gitz repository");
            return;
        }

        GitIndex index = GitIndex.readFromFile(repo.resolve(".gitz/index").toFile());

        // check if there are files staged for commit
        if (index.getEntries().isEmpty()) {
            System.out.println("error: no files added to commit. use 'gitz add <file>' to stage files before committing");
            return;
        }

        if (args.length < 2 || !args[0].equals("-m")) {
            System.out.println("usage: commit -m <message>");
            return;
        }

        String message = args[1];

        String tree = GitRepository.treeFromIndex(repo, index);
        String parentCommit = null;

        try {
            parentCommit = GitRepository.objectFind(repo, "HEAD", "commit", true);
        } catch (Exception e) {
            System.out.println("no previous commit found, creating first commit...");
        }

        String imagePath = null;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("-i") && i + 1 < args.length) {
                imagePath = args[i + 1];
                i++;
            }
        }

        if (imagePath == null) {
            imagePath = repo.resolve(".gitz/img/def.jpg").toString();
        }

        String commit = GitUtils.commitCreate(
                repo, tree, parentCommit,
                GitUtils.gitconfigUserGet(GitUtils.gitconfigRead()),
                OffsetDateTime.now(ZoneOffset.UTC), message
        );

        String storedImagePath = GitUtils.processCommitImage(imagePath, message, commit, repo);

        if (storedImagePath != null) {
            GitRepository.writeFile(new String[]{"img"}, storedImagePath);
        }

        String activeBranch = GitStatus.branchGetActive(repo);
        if (activeBranch == null || activeBranch.isEmpty()) {
            System.out.println("no active branch found. setting HEAD to master");
            activeBranch = "master";
            GitRepository.writeFile(new String[]{"HEAD"}, "ref: refs/heads/master\n");
        }

        GitRepository.writeFile(new String[]{"refs", "heads", activeBranch}, commit + "\n");

        System.out.println("commit successful: " + commit);
    }

}