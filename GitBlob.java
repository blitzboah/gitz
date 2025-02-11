public class GitBlob extends GitObject{
    private byte[] blobdata;

    public GitBlob(){
        super();
    }

    public GitBlob(byte[] data) throws Exception {
        super(data);
    }

    @Override
    public byte[] serialize() {
        return blobdata;
    }

    @Override
    public void deserialize(byte[] data) throws Exception {
        this.blobdata = data;
    }

    @Override
    public String getFmt() {
        return "blob";
    }
}
