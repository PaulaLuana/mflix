package mflix.api.daos;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import mflix.api.models.Session;
import mflix.api.models.User;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Configuration
public class UserDao extends AbstractMFlixDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDao.class);
    private final MongoCollection<User> usersCollection;
    //TODO> Ticket: User Management - do the necessary changes so that the sessions collection
    //returns a Session object
    private final MongoCollection<Document> sessionsCollection;

    private final Logger log;

    @Autowired
    public UserDao(
            MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
        super(mongoClient, databaseName);
        CodecRegistry pojoCodecRegistry =
                fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        usersCollection = db.getCollection("users", User.class).withCodecRegistry(pojoCodecRegistry);
        log = LoggerFactory.getLogger(this.getClass());
        //TODO> Ticket: User Management - implement the necessary changes so that the sessions
        // collection returns a Session objects instead of Document objects.
        sessionsCollection = db.getCollection("sessions");
    }

    /**
     * Inserts the `user` object in the `users` collection.
     *
     * @param user - User object to be added
     * @return True if successful, throw IncorrectDaoOperation otherwise
     */
    public boolean addUser(User user) {
        LOGGER.debug("addUser: {}", user);
        try {
            usersCollection.withWriteConcern(WriteConcern.MAJORITY).insertOne(user);
            return true;
        } catch (MongoWriteException e) {
            throw new IncorrectDaoOperation(String.format("User %s wasn't added", user.getName()), e);
        }
    }

    /**
     * Creates session using userId and jwt token.
     *
     * @param userId - user string identifier
     * @param jwt    - jwt string token
     * @return true if successful
     */
    public boolean createUserSession(String userId, String jwt) {
        LOGGER.debug("createUserSession: userId: {}, jwt: {}", userId, jwt);
        Bson filter = new Document("user_id", userId);
        Bson update = Updates.set("jwt", jwt);
        UpdateOptions options = new UpdateOptions().upsert(true);
        try {
            UpdateResult updateResult = sessionsCollection.updateOne(filter, update, options);
            return updateResult.wasAcknowledged();
        } catch (MongoWriteException e) {
            LOGGER.error("Session creation of user {} was failed; Cause: {}", userId, e);
            return false;
        }
    }

    /**
     * Returns the User object matching the an email string value.
     *
     * @param email - email string to be matched.
     * @return User object or null.
     */
    public User getUser(String email) {
        User user = usersCollection.find(Filters.eq("email", email)).first();
        //TODO> Ticket: User Management - implement the query that returns the first User object.
        return user;
    }

    /**
     * Given the userId, returns a Session object.
     *
     * @param userId - user string identifier.
     * @return Session object or null.
     */
    public Session getUserSession(String userId) {
        Document document = sessionsCollection.find(Filters.eq("user_id", userId)).first();
        if(document == null) {
            return null;
        }
        Session session = new Session();
        session.setJwt(document.getString("jwt"));
        session.setUserId(userId);

        return session;
    }

    public boolean deleteUserSessions(String userId) {
        try{
            sessionsCollection.deleteOne(Filters.eq("user_id", userId));
            return true;
        }catch (Exception e){
            return false;
        }
        //TODO> Ticket: User Management - implement the delete user sessions method
    }

    /**
     * Removes the user document that match the provided email.
     *
     * @param email - of the user to be deleted.
     * @return true if user successfully removed
     */
    public boolean deleteUser(String email) {
        boolean isSessionDeleted = deleteUserSessions(email);
        if (isSessionDeleted) {
            try {
                Bson deleteFilter = new Document("email", email);
                DeleteResult deleteResult = usersCollection.deleteOne(deleteFilter);
                return deleteResult.wasAcknowledged();
            } catch (MongoWriteException e) {
                LOGGER.error("Deletion of user with {} email was failed; Cause: {}", email, e);
                return false;
            }
        } else {
            LOGGER.error("Sessions of user with email {} were not deleted", email);
            return false;
        }
    }

    /**
     * Updates the preferences of an user identified by `email` parameter.
     *
     * @param email           - user to be updated email
     * @param userPreferences - set of preferences that should be stored and replace the existing
     *                        ones. Cannot be set to null value
     * @return User object that just been updated.
     */
    public boolean updateUserPreferences(String email, Map<String, ?> userPreferences) {
        if (userPreferences == null) {
            throw new IncorrectDaoOperation("userPreferences is null");
        }
        Bson emailFilter = new Document("email", email);
        User userToUpdate = usersCollection.find(emailFilter).first();
        if (userToUpdate == null) {
            throw new IncorrectDaoOperation(String.format("User by email %s not found", email));
        }
        Map<String, String> oldPrefs = userToUpdate.getPreferences();
        if (oldPrefs == null) {
            oldPrefs = new HashMap<>();
        }

        for (Map.Entry<String, ?> entry : userPreferences.entrySet()) {
            oldPrefs.put(entry.getKey(), (String) entry.getValue());
        }

        try {
            UpdateResult result = usersCollection.updateOne(emailFilter, Updates.set("preferences", oldPrefs));
            return result.wasAcknowledged();
        } catch (MongoWriteException wrEx) {
            throw new IncorrectDaoOperation("Some error occurred while updating comment" + wrEx.getError().getCategory());
        }
    }
}
