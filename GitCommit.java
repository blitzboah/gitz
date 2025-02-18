import java.util.HashMap;
import java.util.Map;

public class GitCommit extends GitObject{

    private Map<byte[], byte[]> data;

    public GitCommit(){
        super();
    }

    public GitCommit(byte[] data) throws Exception {
        super(data);
    }

    public Map<byte[], byte[]> getKvlm(){
        return data;
    }

    @Override
    public void init(){
        this.data = new HashMap<>();
    }

    @Override
    public byte[] serialize() {
        return GitUtils.kvlmSerialize(data);
    }

    @Override
    public void deserialize(byte[] data) throws Exception {
        this.data = GitUtils.kvlmParse(data, 0, null);
    }

    @Override
    public String getFmt() {
        return "commit";
    }
}
