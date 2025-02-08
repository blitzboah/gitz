public class Main {
    public static void main(String[] args) throws Exception {
        String command = "";
        String flag = "";

        GitRepository gitRepository = new GitRepository();

        try {
            command = args[0];
            flag = args[1];
        }catch (IndexOutOfBoundsException e){
            e.getMessage();
        }

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
