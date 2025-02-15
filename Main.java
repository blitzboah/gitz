import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws Exception {
        String command = "";
        String flag = "";

        if(args.length == 0){
            System.out.println("try using <gitz init> if its not a gitz dir");
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

    public static void catFile(Path path, String obj, String type){
        String sha = obj.length() == 40 ? obj : GitRepository.objectFind(path, obj, type);

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
}
