import java.util.ArrayList;
import java.util.List;

public class GitTree extends GitObject {
    private List<GitTreeLeaf> items;

    public GitTree() {
        super();
    }

    public GitTree(byte[] data) throws Exception {
        super(data);
    }

    @Override
    public void init() {
        this.items = new ArrayList<>();
    }

    @Override
    public byte[] serialize() {
        return GitTreeLeaf.treeSerialize(items);
    }

    @Override
    public void deserialize(byte[] data) throws Exception {
        this.items = GitTreeLeaf.treeParse(data);
    }

    @Override
    public String getFmt() {
        return "tree";
    }

    public List<GitTreeLeaf> getItems() {
        return items;
    }

    public void setItems(List<GitTreeLeaf> items) {
        this.items = items;
    }


}
