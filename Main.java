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
            case "git-flag" -> {
                System.out.println("some info will get printed");
            }
            default -> System.out.println("type correctly lil bro");
        }
    }
}
