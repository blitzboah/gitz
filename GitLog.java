import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GitLog {
    public static void cmdLog() throws IOException {
        Path repo = GitRepository.repoFind(".", true);

        assert repo != null;
        Path headPath = repo.resolve(".gitz/HEAD");
        String headContent = Files.readString(headPath);

        if(headContent.startsWith("ref: ")){
            String refPath = headContent.substring(5).trim();

            Path branchPath = repo.resolve(".gitz").resolve(refPath);
            if(!Files.exists(branchPath)){
                System.out.println("your current branch 'master' does not have any commits yet");
                return;
            }
            String commitSha = Files.readString(branchPath).trim();

            printLog(commitSha, new HashSet<>());
        }
    }

    private static void printLog(String sha, Set<String> seen){
        if(seen.contains(sha)){
            return;
        }

        GitObject obj = GitRepository.objectRead(sha);
        if(!(obj instanceof GitCommit)){
            System.out.println("not a commit object");
            return;
        }

        GitCommit commit = (GitCommit) obj;
        Map<byte[], byte[]> kvlm = commit.getKvlm();

        //print log
        System.out.println("commit: "+sha);

        byte[] author = findKey(kvlm, "author");
        if(author != null){
            System.out.println("author: "+new String(author, StandardCharsets.UTF_8));
        }

        byte[] msg = kvlm.get(null);
        if(msg != null){
            System.out.println("\n   " + new String(msg, StandardCharsets.UTF_8));
        }
        System.out.println();

        //handle parents
        byte[] parentData = findKey(kvlm, "parent");
        if(parentData != null){
            String parentSha = new String(parentData, StandardCharsets.UTF_8);
            printLog(parentSha, seen);
        }
    }

    //just a helper function cuz if i directly get bytes using getBytes() i'll get the content not the reference to the key
    private static byte[] findKey(Map<byte[], byte[]> kvlm, String key){
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        for (Map.Entry<byte[], byte[]> entry: kvlm.entrySet()){
            if(entry.getKey() != null && Arrays.equals(entry.getKey(),keyBytes)){
                return entry.getValue();
            }
        }

        return null;
    }
}
