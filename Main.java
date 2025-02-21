import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        String sha = obj.length() == 40 ? obj : GitRepository.objectFind(path, obj, type, false);

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

    public static void cmdLsTree(String[] args){
        Path repo = GitRepository.repoFind(".", true);
        String treeRef = args[0];
        boolean recursive = args.length > 1 && args[1].equals("-r");
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

}
