package mflix.api.daos;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.ReadConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import mflix.api.models.Comment;
import mflix.api.models.Critic;
import mflix.api.models.User;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Component
public class CommentDao extends AbstractMFlixDao {

    public static String COMMENT_COLLECTION = "comments";

    private MongoCollection<Comment> commentCollection;

    private CodecRegistry pojoCodecRegistry;

    private final Logger log;

    @Autowired
    public CommentDao(MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
        super(mongoClient, databaseName);
        log = LoggerFactory.getLogger(this.getClass());
        this.db = this.mongoClient.getDatabase(MFLIX_DATABASE);
        this.pojoCodecRegistry =
                fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        this.commentCollection =
                db.getCollection(COMMENT_COLLECTION, Comment.class).withCodecRegistry(pojoCodecRegistry);
    }

    /**
     * Returns a Comment object that matches the provided id string.
     *
     * @param id - comment identifier
     * @return Comment object corresponding to the identifier value
     */
    public Comment getComment(String id) {
        try {
            Comment comment = commentCollection.find(Filters.eq("_id", new ObjectId(id))).first();
            if(comment == null) {
                comment = new Comment();
                comment.setId(id);
            }
            return comment;
        } catch (Exception ex) {
            throw new IncorrectDaoOperation("Some error occurred while insert");
        }

    }

    /**
     * Adds a new Comment to the collection. The equivalent instruction in the mongo shell would be:
     *
     * <p>db.comments.insertOne({comment})
     *
     * <p>
     *
     * @param comment - Comment object.
     * @throw IncorrectDaoOperation if the insert fails, otherwise
     * returns the resulting Comment object.
     */
    public Comment addComment(Comment comment) {
        try {
            commentCollection.insertOne(comment);
            comment = getComment(comment.getId());
        } catch (Exception ex) {
            throw new IncorrectDaoOperation("Some error occurred while insert");
        }
        return comment;
    }


    /**
     * Updates the comment text matching commentId and user email. This method would be equivalent to
     * running the following mongo shell command:
     *
     * <p>db.comments.update({_id: commentId}, {$set: { "text": text, date: ISODate() }})
     *
     * <p>
     *
     * @param commentId - comment id string value.
     * @param text      - comment text to be updated.
     * @param email     - user email.
     * @return true if successfully updates the comment text.
     */
    public boolean updateComment(String commentId, String text, String email) {
        Bson filter = Filters.and(
                Filters.eq("email", email),
                Filters.eq("_id", new ObjectId(commentId)));
        Bson update = Updates.set("text", text);
        UpdateOptions options = new UpdateOptions().upsert(true);
        try {
            UpdateResult updateResult = commentCollection.updateOne(filter, update, options);
            return updateResult.wasAcknowledged();
        } catch (MongoWriteException e) {
            return false;
        }
    }

    /**
     * Deletes comment that matches user email and commentId.
     *
     * @param commentId - commentId string value.
     * @param email     - user email value.
     * @return true if successful deletes the comment.
     */
    public boolean deleteComment(String commentId, String email) {
        Bson filter = Filters.and(
                Filters.eq("email", email),
                Filters.eq("_id", new ObjectId(commentId)));
        try {
            DeleteResult deleteResult = commentCollection.deleteOne(filter);
            return deleteResult.getDeletedCount() != 0;
        } catch (MongoWriteException e) {
            return false;
        }
//        Solution #2
//        try {
//            Comment comment = commentCollection.findOneAndDelete(filter);
//            return comment != null;
//        } catch (MongoWriteException wrEx) {
//            throw new IncorrectDaoOperation("Deletion was failed", wrEx);
//        }
    }

    /**
     * Ticket: User Report - produce a list of users that comment the most in the website. Query the
     * `comments` collection and group the users by number of comments. The list is limited to up most
     * 20 commenter.
     *
     * @return List {@link Critic} objects.
     */
    public List<Critic> mostActiveCommenters() {
        Bson groupStage = Aggregates.group("$email", Accumulators.sum("count", 1));
        Bson limit = Aggregates.limit(20);
        Bson sortStage = Aggregates.sort(Sorts.descending("count"));
        return commentCollection
                .withReadConcern(ReadConcern.MAJORITY)
                .aggregate(Arrays.asList(groupStage, sortStage, limit), Critic.class)
                .into(new ArrayList<>());

//        Solution #2
//        return commentCollection.withReadConcern(ReadConcern.MAJORITY).aggregate(
//                Arrays.asList(
//                        Aggregates.group("$email", Accumulators.sum("count", 1)),
//                        Aggregates.sort(Sorts.descending("count")),
//                        Aggregates.limit(20)
//                ), Critic.class).into(new ArrayList<>());
    }
}