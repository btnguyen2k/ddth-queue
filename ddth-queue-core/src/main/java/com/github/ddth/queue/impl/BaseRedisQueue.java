package com.github.ddth.queue.impl;

import com.github.ddth.commons.redis.JedisConnector;
import com.github.ddth.queue.IQueue;
import com.github.ddth.queue.IQueueMessage;
import com.github.ddth.queue.internal.utils.RedisUtils;
import com.github.ddth.queue.utils.QueueException;
import redis.clients.jedis.BinaryJedisCommands;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Base Redis implementation of {@link IQueue}.
 *
 * <p>
 * Implementation:
 * <ul>
 * <li>A hash to store message, format {queue_id => message}. See
 * {@link #setRedisHashName(String)}.
 * <li>A list to act as a queue of message's queue_id. See
 * {@link #setRedisListName(String)}.
 * <li>A sorted set to act as ephemeral storage of message's queue_id, score is
 * message's timestamp. See {@link #setRedisSortedSetName(String)}.</li>
 * </ul>
 * </p>
 *
 * <p>Features:</p>
 * <ul>
 * <li>Queue-size support: yes</li>
 * <li>Ephemeral storage support: yes</li>
 * <li>Ephemeral-size support: yes</li>
 * </ul>
 *
 * @author Thanh Ba Nguyen <bnguyen2k@gmail.com>
 * @since 0.6.2.6
 */
public abstract class BaseRedisQueue<ID, DATA> extends AbstractEphemeralSupportQueue<ID, DATA> {
    public final static String DEFAULT_PASSWORD = null;
    public final static String DEFAULT_HASH_NAME = "queue_h";
    public final static String DEFAULT_LIST_NAME = "queue_l";
    public final static String DEFAULT_SORTED_SET_NAME = "queue_s";

    private String redisPassword = DEFAULT_PASSWORD;
    private JedisConnector jedisConnector;
    /**
     * Flag to mark if the Redis resource (e.g. Redis client pool) is created
     * and handled by the lock instance.
     */
    protected boolean myOwnRedis = true;

    private String _redisHashName = DEFAULT_HASH_NAME;
    private byte[] redisHashName = _redisHashName.getBytes(StandardCharsets.UTF_8);

    private String _redisListName = DEFAULT_LIST_NAME;
    private byte[] redisListName = _redisListName.getBytes(StandardCharsets.UTF_8);

    private String _redisSortedSetName = DEFAULT_SORTED_SET_NAME;
    private byte[] redisSortedSetName = _redisSortedSetName.getBytes(StandardCharsets.UTF_8);

    /**
     * Get the current {@link JedisConnector} used by this queue.
     *
     * @return
     */
    public JedisConnector getJedisConnector() {
        return jedisConnector;
    }

    /**
     * Setter for {@link #jedisConnector}.
     *
     * @param jedisConnector
     * @param setMyOwnRedis
     * @return
     * @since 0.7.1
     */
    protected BaseRedisQueue<ID, DATA> setJedisConnector(JedisConnector jedisConnector, boolean setMyOwnRedis) {
        if (myOwnRedis && this.jedisConnector != null) {
            this.jedisConnector.destroy();
        }
        this.jedisConnector = jedisConnector;
        myOwnRedis = setMyOwnRedis;
        return this;
    }

    /**
     * Set the external {@link JedisConnector} to be used by this queue.
     *
     * @param jedisConnector
     * @return
     */
    public BaseRedisQueue<ID, DATA> setJedisConnector(JedisConnector jedisConnector) {
        return setJedisConnector(jedisConnector, false);
    }

    /**
     * Redis' password.
     *
     * @return
     */
    public String getRedisPassword() {
        return redisPassword;
    }

    /**
     * Redis' password.
     *
     * @param redisPassword
     * @return
     */
    public BaseRedisQueue<ID, DATA> setRedisPassword(String redisPassword) {
        this.redisPassword = redisPassword;
        return this;
    }

    /**
     * Name of the Redis hash to store queue messages.
     *
     * @return
     */
    public String getRedisHashName() {
        return _redisHashName;
    }

    /**
     * Name of the Redis hash to store queue messages.
     *
     * @return
     */
    public byte[] getRedisHashNameAsBytes() {
        return redisHashName;
    }

    /**
     * Name of the Redis hash to store queue messages.
     *
     * @param redisHashName
     * @return
     */
    public BaseRedisQueue<ID, DATA> setRedisHashName(String redisHashName) {
        _redisHashName = redisHashName;
        this.redisHashName = _redisHashName.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    /**
     * Name of the Redis list to store queue message ids.
     *
     * @return
     */
    public String getRedisListName() {
        return _redisListName;
    }

    /**
     * Name of the Redis list to store queue message ids.
     *
     * @return
     */
    public byte[] getRedisListNameAsBytes() {
        return redisListName;
    }

    /**
     * Name of the Redis list to store queue message ids.
     *
     * @param redisListName
     * @return
     */
    public BaseRedisQueue<ID, DATA> setRedisListName(String redisListName) {
        _redisListName = redisListName;
        this.redisListName = _redisListName.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    /**
     * Name of the Redis sorted-set to store ephemeral message ids.
     *
     * @return
     */
    public String getRedisSortedSetName() {
        return _redisSortedSetName;
    }

    /**
     * Name of the Redis sorted-set to store ephemeral message ids.
     *
     * @return
     */
    public byte[] getRedisSortedSetNameAsBytes() {
        return redisSortedSetName;
    }

    /**
     * Name of the Redis sorted-set to store ephemeral message ids.
     *
     * @param redisSortedSetName
     * @return
     */
    public BaseRedisQueue<ID, DATA> setRedisSortedSetName(String redisSortedSetName) {
        _redisSortedSetName = redisSortedSetName;
        this.redisSortedSetName = _redisSortedSetName.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    /*----------------------------------------------------------------------*/
    /**
     * LUA script to take a message out of queue.
     */
    private String scriptTake;
    private byte[] scriptTakeAsBytes;

//    /**
//     * LUA script to move a message from ephemeral storage to queue storage.
//     */
//    private String scriptMove;
//    private byte[] scriptMoveAsBytes;

    /**
     * LUA script to take a message out of queue.
     *
     * @return
     */
    public String getScriptTake() {
        return scriptTake;
    }

    /**
     * LUA script to take a message out of queue.
     *
     * @return
     * @since 1.0.0
     */
    protected byte[] getScriptTakeAsBytes() {
        if (scriptTakeAsBytes == null) {
            scriptTakeAsBytes = scriptTake != null ? scriptTake.getBytes(StandardCharsets.UTF_8) : null;
        }
        return scriptTakeAsBytes;
    }

    /**
     * LUA script to take a message out of queue.
     *
     * <p>
     * Normally queue implementations will build this script; no need to "set"
     * it from outside.
     * </p>
     *
     * @param scriptTake
     * @return
     */
    public BaseRedisQueue<ID, DATA> setScriptTake(String scriptTake) {
        this.scriptTake = scriptTake;
        scriptTakeAsBytes = scriptTake != null ? scriptTake.getBytes(StandardCharsets.UTF_8) : null;
        return this;
    }

//    /**
//     * LUA script to move a message from ephemeral storage to queue storage.
//     *
//     * @return
//     */
//    public String getScriptMove() {
//        return scriptMove;
//    }
//
//    /**
//     * LUA script to move a message from ephemeral storage to queue storage.
//     *
//     * @return
//     * @since 1.0.0
//     */
//    protected byte[] getScriptMoveAsBytes() {
//        if (scriptMoveAsBytes == null) {
//            scriptMoveAsBytes = scriptMove != null ? scriptMove.getBytes(StandardCharsets.UTF_8) : null;
//        }
//        return scriptMoveAsBytes;
//    }
//
//    /**
//     * LUA script to move a message from ephemeral storage to queue storage.
//     *
//     * <p>
//     * Normally queue implementations will build this script; no need to "set"
//     * it from outside.
//     * </p>
//     *
//     * @param scriptMove
//     * @return
//     */
//    public BaseRedisQueue<ID, DATA> setScriptMove(String scriptMove) {
//        this.scriptMove = scriptMove;
//        scriptMoveAsBytes = scriptMove != null ? scriptMove.getBytes(StandardCharsets.UTF_8) : null;
//        return this;
//    }

    /**
     * Build a {@link JedisConnector} instance for my own use.
     *
     * @return
     * @since 0.6.2.6
     */
    protected abstract JedisConnector buildJedisConnector();

    /**
     * Init method.
     *
     * @return
     * @throws Exception
     */
    public BaseRedisQueue<ID, DATA> init() throws Exception {
        if (jedisConnector == null) {
            setJedisConnector(buildJedisConnector(), true);
        }

        if (isEphemeralDisabled()) {
            /*
             * Script details (ephemeral is disabled): lpop qId from the List
             * and hget message's content from the Hash and remove it from the
             * Hash, atomically. Finally, the message's content is returned.
             *
             * Script's first argument (ARGV[1]) is the qId's associated
             * timestamp to be used as score value for the SortedSet entry.
             */
            scriptTake = "local qid=redis.call(\"lpop\",\"{0}\"); if qid then "
                    + "local qcontent=redis.call(\"hget\", \"{2}\", qid); "
                    + "redis.call(\"hdel\", \"{2}\", qid); return qcontent " + "else return nil end";
        } else {
            /*
             * Script details (ephemeral is enabled): lpop qId from the List and
             * zadd {ARGV[1]:qId} to the SortedSet and hget message's content
             * from the Hash, atomically. Finally, the message's content is
             * returned.
             */
            scriptTake = "local qid=redis.call(\"lpop\",\"{0}\"); if qid then "
                    + "redis.call(\"zadd\", \"{1}\", ARGV[1], qid); return redis.call(\"hget\", \"{2}\", qid) "
                    + "else return nil end";
        }
        scriptTake = MessageFormat.format(scriptTake, _redisListName, _redisSortedSetName, _redisHashName);

//        /*
//         * Script details: remove qId from the SortedSet and rpush it to the
//         * List, atomically.
//         *
//         * Script's first argument (ARGV[1]) is qId.
//         */
//        scriptMove = "local result=redis.call(\"zrem\",\"{0}\",ARGV[1]); if result then "
//                + "redis.call(\"rpush\", \"{1}\",  ARGV[1]); return 1; else return 0; end";
//        scriptMove = MessageFormat.format(scriptMove, _redisSortedSetName, _redisListName);

        super.init();

        if (jedisConnector == null) {
            throw new IllegalStateException("Jedis connector is null.");
        }

        return this;
    }

    /**
     * Destroy method.
     */
    public void destroy() {
        try {
            super.destroy();
        } finally {
            jedisConnector = RedisUtils.closeJedisConnector(jedisConnector, myOwnRedis);
        }
    }

    /*----------------------------------------------------------------------*/

    /**
     * Get {@link BinaryJedisCommands} instance.
     *
     * @return
     * @since 0.6.2.6
     */
    protected abstract BinaryJedisCommands getBinaryJedisCommands();

    /**
     * Close the unused {@link BinaryJedisCommands}.
     *
     * @param jedisCommands
     * @since 0.6.2.6
     */
    protected abstract void closeJedisCommands(BinaryJedisCommands jedisCommands);

    /**
     * Remove a message completely.
     *
     * <p>Implementation note:</p>
     * <ul>
     * <li>Message should no longer exist after method call regardless of return value ({@code true} or {@code false})</li>
     * <li>In case of error, throws {@link QueueException}</li>
     * </ul>
     *
     * @param msg
     * @return {@code true} if the message has been removed, {@code false} means the message does not exist for completely removal
     * @throws QueueException if error (e.g. IOException while communicating with Redis server)
     */
    protected abstract boolean remove(IQueueMessage<ID, DATA> msg) throws QueueException;

    /**
     * Store a new message.
     *
     * <p>Implementation note:</p>
     * <ul>
     * <li>Message's ID must be successfully pushed into the "queue-list" so that the method call is considered successful.
     * If message's id has not pushed to the "queue-list", this method must return {@code false}.</li>
     * <li>Caller can retry if method returns {@code false}. Implementation's responsibility to ensure message is not duplicated in storage.</li>
     * <li>In case of error, throws {@link QueueException}</li>
     * </ul>
     *
     * @param msg
     * @return
     */
    protected abstract boolean storeNew(IQueueMessage<ID, DATA> msg);

    /**
     * Re-store an old message (called by {@link #requeue(IQueueMessage)} or
     * {@link #requeueSilent(IQueueMessage)}.
     *
     * <p>Implementation note:</p>
     * <ul>
     * <li>Message's ID must be successfully pushed into the "queue-list" so that the method call is considered successful.
     * If message's id has not pushed to the "queue-list", this method must return {@code false}.</li>
     * <li>Caller can retry if method returns {@code false}. Implementation's responsibility to ensure message is not duplicated in storage.</li>
     * <li>In case of error, throws {@link QueueException}</li>
     * </ul>
     *
     * @param msg
     * @return
     */
    protected abstract boolean storeOld(IQueueMessage<ID, DATA> msg);

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean doPutToQueue(IQueueMessage<ID, DATA> msg, PutToQueueCase queueCase) {
        return queueCase == null || queueCase == PutToQueueCase.NEW || isEphemeralDisabled() ?
                storeNew(msg) :
                storeOld(msg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finish(IQueueMessage<ID, DATA> msg) {
        /** according to spec of {@link #remove(IQueueMessage)}: both returned values {@code true/false} are acceptable */
        remove(msg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IQueueMessage<ID, DATA>> getOrphanMessages(long thresholdTimestampMs) {
        Collection<IQueueMessage<ID, DATA>> orphanMessages = new HashSet<>();
        if (!isEphemeralDisabled()) {
            BinaryJedisCommands jc = getBinaryJedisCommands();
            try {
                long now = System.currentTimeMillis();
                Collection<IQueueMessage<ID, DATA>> result = new HashSet<>();
                byte[] min = "0".getBytes();
                byte[] max = String.valueOf(now - thresholdTimestampMs).getBytes();
                Set<byte[]> fields = jc.zrangeByScore(getRedisSortedSetNameAsBytes(), min, max, 0, 100);
                for (byte[] field : fields) {
                    byte[] data = jc.hget(getRedisHashNameAsBytes(), field);
                    IQueueMessage<ID, DATA> msg = deserialize(data);
                    if (msg != null) {
                        result.add(msg);
                    }
                }
                return result;
            } finally {
                closeJedisCommands(jc);
            }
        }
        return orphanMessages;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int queueSize() {
        BinaryJedisCommands jc = getBinaryJedisCommands();
        try {
            Long result = jc.llen(getRedisListNameAsBytes());
            return result != null ? result.intValue() : 0;
        } finally {
            closeJedisCommands(jc);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int ephemeralSize() {
        if (isEphemeralDisabled()) {
            return 0;
        }
        BinaryJedisCommands jc = getBinaryJedisCommands();
        try {
            Long result = jc.zcard(getRedisSortedSetNameAsBytes());
            return result != null ? result.intValue() : 0;
        } finally {
            closeJedisCommands(jc);
        }
    }
}
