public class GitTreeLeaf extends GitObject{

    @Override
    public byte[] serialize() {
        return new byte[0];
    }

    @Override
    public void deserialize(byte[] data) throws Exception {

    }

    @Override
    public String getFmt() {
        return "";
    }
}
