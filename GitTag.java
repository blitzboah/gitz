import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class GitTag extends GitCommit{

    public GitTag(){
        super();
    }

    public GitTag(byte[] data){
        this.data = GitUtils.kvlmParse(data, 0, null);
    }

    @Override
    public String getFmt(){
        return "tag";
    }

    public void setKvlm(Map<byte[], byte[]> kvlm){
        this.data = kvlm;
    }

    public static void tagCreate(Path repo, String name, String ref, boolean createTagObj) throws Exception {
        String sha = GitRepository.objectFind(repo, ref, null, true);

        if(sha == null){
            throw new Exception("object " + ref + "not found");
        }

        if(createTagObj){
            GitTag tag = new GitTag();
            Map<byte[], byte[]> kvlm = new HashMap<>();

            kvlm.put("object".getBytes(StandardCharsets.UTF_8), sha.getBytes(StandardCharsets.UTF_8));
            kvlm.put("type".getBytes(StandardCharsets.UTF_8), sha.getBytes(StandardCharsets.UTF_8));
            kvlm.put("tag".getBytes(StandardCharsets.UTF_8), name.getBytes(StandardCharsets.UTF_8));
            kvlm.put("tagger".getBytes(StandardCharsets.UTF_8), "blitz <blitzboi31@gmail.com>".getBytes(StandardCharsets.UTF_8));

            kvlm.put(null, "a message by blitz, which won't let u customize the msg".getBytes(StandardCharsets.UTF_8));

            tag.setKvlm(kvlm);

            String tagSha = GitRepository.objectWrite(tag);
            refCreate(repo, "tags/"+name, tagSha);
        }
        else{
            refCreate(repo, "tags/"+name, sha);
        }

    }

    private static void refCreate(Path repo, String refName, String sha) throws IOException {
        Path refPath = repo.resolve(".gitz/refs/"+refName);

        Files.createDirectories(refPath.getParent());
        Files.writeString(refPath, sha+"\n");
    }
}
