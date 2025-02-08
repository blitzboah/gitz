import java.nio.file.Path;
import java.nio.file.Paths;

public class GitRepository {
    private final Path worktree;
    private final Path gitdir;

    public GitRepository() {
        this.worktree = Paths.get(System.getProperty("user.dir"));
        this.gitdir = worktree.resolve(".gitz");
    }

    void gitInit() throws Exception {
        if(gitdir.toFile().exists()) {
            System.out.println("already a gitz repo");
        }
        else {

            if(gitdir.toFile().mkdir()) {

                System.out.println("gitz repo initialized");

                assert repoDir(new String[]{"branches"}, true) != null;
                assert repoDir(new String[]{"objects"}, true) != null;
                assert repoDir(new String[]{"refs", "tags"}, true) != null;
                assert repoDir(new String[]{"refs", "heads"}, true) != null;
            }

            else {
                System.out.println("failed to init gitz repo");
            }
        }
    }

    Path repoPath(String... path){
        return gitdir.resolve(Paths.get("", path));
    }

    Path repoFile(String[] path, boolean mkdir) throws Exception {
        if(repoDir(path, mkdir) == null)
            return repoPath(path);
        return null;
    }

    Path repoDir(String[] path, boolean mkdir) throws Exception {
        Path resolevedPath = repoPath(path);

        if(resolevedPath.toFile().exists()){
            if(resolevedPath.toFile().isDirectory())
                return resolevedPath;
            else
                throw new Exception("not a directory");
        }

        if (mkdir){
            resolevedPath.toFile().mkdirs();
            return resolevedPath;
        }
        else
            return null;
    }
}