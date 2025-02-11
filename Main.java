import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        String command = "";
        String flag = "";

        if(args.length == 0){
            System.out.println("try using init if its not a gitz dir");
        }
        GitRepository gitRepository = new GitRepository();

        command = args[0];
        //flag = args[1];


        switch (command){
            case "init" -> {
                gitRepository.gitInit();
            }
            case "cat-file" -> {
                String[] newArgs = new String[args.length-1];
                System.arraycopy(args, 0, newArgs, 1, newArgs.length);
                cmdCatFile(newArgs);
                System.out.println("some info will get printed");
            }
            default -> System.out.println("type correctly lil bro");
        }
    }

    public static void cmdCatFile(String[] args) throws Exception{
        if (args.length < 2){
            System.out.println("usage: cat-file <object> [type]");
            return;
        }

        String obj = args[0];
        String type = args[1];
    }
}
