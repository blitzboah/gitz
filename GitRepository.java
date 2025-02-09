import java.io.FileWriter;
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
                repoDir(new String[]{"branches"}, true);
                repoDir(new String[]{"objects"}, true);
                repoDir(new String[]{"refs", "tags"}, true);
                repoDir(new String[]{"refs", "heads"},true);

                writeFile(new String[]{"HEADS"}, "ref: refs/heads/master\n"); //master as linus intended
                writeFile(new String[]{"description"}, "unnamed repo, edit this file to name repo.\n");

                System.out.println("gitz repo initialized");
            }

            else {
                System.out.println("failed to init gitz repo");
            }
        }
    }

    // computes the path
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
            if(resolevedPath.toFile().mkdirs())
                return resolevedPath;
        }
        return null;
    }



    void writeFile(String[] path, String content) throws Exception {
        Path filePath = repoFile(path, false);
        try (FileWriter fw = new FileWriter(filePath.toFile())){
            fw.write(content);
        }
    }
}