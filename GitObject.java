public abstract class GitObject {
    protected byte[] data;

    public GitObject(){
        this.init();
    }

    public GitObject(byte[] data) throws Exception {
        if(data != null){
            this.deserialize(data);
        }
        else{
            this.init();
        }
    }

    public abstract byte[] serialize(byte[] repo);

    public abstract void deserialize(byte[] data) throws Exception;

    public void init(){
        //do nothing br
    }
}
