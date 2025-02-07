import java.io.File;

public class GitRepository {
    private final String worktree = System.getProperty("user.dir");
    private final File gitdir = new File(worktree + "/.gitz");

    void gitInit(){
        if(gitdir.exists()) {
            System.out.println("already a gitz repo");
        }
        else {
            if(gitdir.mkdir()){
                System.out.println("gitz repo initialized");

            }
            else {
                System.out.println("failed to init gitz repo");
            }
        }
    }

    void createFiles(){

    }

    void createDirs(){

    }
}