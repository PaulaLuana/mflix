package mflix.api.models;

import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonProperty;

public class Session extends Document {

    @BsonProperty(value = "user_id")
    private String userId;

    private String jwt;

    public Session(Document user_id) {
        super();
    }

    public Session() {

    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getJwt() {
        return jwt;
    }

    public void setJwt(String jwt) {
        this.jwt = jwt;
    }
}
