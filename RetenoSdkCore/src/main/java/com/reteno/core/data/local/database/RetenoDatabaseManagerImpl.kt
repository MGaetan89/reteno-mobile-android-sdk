package com.reteno.core.data.local.database

import android.content.ContentValues
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.reteno.core.data.local.database.DbSchema.AppInboxSchema.COLUMN_APP_INBOX_ID
import com.reteno.core.data.local.database.DbSchema.AppInboxSchema.COLUMN_APP_INBOX_TIME
import com.reteno.core.data.local.database.DbSchema.AppInboxSchema.TABLE_NAME_APP_INBOX
import com.reteno.core.data.local.database.DbSchema.COLUMN_TIMESTAMP
import com.reteno.core.data.local.database.DbSchema.DeviceSchema.COLUMN_DEVICE_ROW_ID
import com.reteno.core.data.local.database.DbSchema.DeviceSchema.TABLE_NAME_DEVICE
import com.reteno.core.data.local.database.DbSchema.EventSchema.COLUMN_EVENT_OCCURRED
import com.reteno.core.data.local.database.DbSchema.EventSchema.COLUMN_EVENT_ROW_ID
import com.reteno.core.data.local.database.DbSchema.EventSchema.TABLE_NAME_EVENT
import com.reteno.core.data.local.database.DbSchema.EventsSchema.COLUMN_EVENTS_DEVICE_ID
import com.reteno.core.data.local.database.DbSchema.EventsSchema.COLUMN_EVENTS_EXTERNAL_USER_ID
import com.reteno.core.data.local.database.DbSchema.EventsSchema.COLUMN_EVENTS_ID
import com.reteno.core.data.local.database.DbSchema.EventsSchema.TABLE_NAME_EVENTS
import com.reteno.core.data.local.database.DbSchema.InteractionSchema.COLUMN_INTERACTION_ROW_ID
import com.reteno.core.data.local.database.DbSchema.InteractionSchema.COLUMN_INTERACTION_TIME
import com.reteno.core.data.local.database.DbSchema.InteractionSchema.TABLE_NAME_INTERACTION
import com.reteno.core.data.local.database.DbSchema.RecomEventSchema.COLUMN_RECOM_EVENT_OCCURRED
import com.reteno.core.data.local.database.DbSchema.RecomEventSchema.COLUMN_RECOM_EVENT_ROW_ID
import com.reteno.core.data.local.database.DbSchema.RecomEventSchema.TABLE_NAME_RECOM_EVENT
import com.reteno.core.data.local.database.DbSchema.RecomEventsSchema.COLUMN_RECOM_VARIANT_ID
import com.reteno.core.data.local.database.DbSchema.RecomEventsSchema.TABLE_NAME_RECOM_EVENTS
import com.reteno.core.data.local.database.DbSchema.UserAddressSchema.COLUMN_ADDRESS
import com.reteno.core.data.local.database.DbSchema.UserAddressSchema.COLUMN_POSTCODE
import com.reteno.core.data.local.database.DbSchema.UserAddressSchema.COLUMN_REGION
import com.reteno.core.data.local.database.DbSchema.UserAddressSchema.COLUMN_TOWN
import com.reteno.core.data.local.database.DbSchema.UserAddressSchema.TABLE_NAME_USER_ADDRESS
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_CUSTOM_FIELDS
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_EMAIL
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_FIRST_NAME
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_LANGUAGE_CODE
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_LAST_NAME
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_PHONE
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_TIME_ZONE
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.TABLE_NAME_USER_ATTRIBUTES
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_DEVICE_ID
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_EXTERNAL_USER_ID
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_GROUP_NAMES_EXCLUDE
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_GROUP_NAMES_INCLUDE
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_SUBSCRIPTION_KEYS
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_USER_ROW_ID
import com.reteno.core.data.local.database.DbSchema.UserSchema.TABLE_NAME_USER
import com.reteno.core.data.local.model.appinbox.AppInboxMessageDb
import com.reteno.core.data.local.model.device.DeviceDb
import com.reteno.core.data.local.model.event.EventDb
import com.reteno.core.data.local.model.event.EventsDb
import com.reteno.core.data.local.model.interaction.InteractionDb
import com.reteno.core.data.local.model.recommendation.RecomEventDb
import com.reteno.core.data.local.model.recommendation.RecomEventsDb
import com.reteno.core.data.local.model.user.UserDb
import com.reteno.core.util.Logger
import com.reteno.core.util.allElementsNotNull
import net.sqlcipher.Cursor
import net.sqlcipher.SQLException

class RetenoDatabaseManagerImpl(private val database: RetenoDatabase) : RetenoDatabaseManager {

    private val contentValues = ContentValues()

    //==============================================================================================
    override fun isDatabaseEmpty(): Boolean {
        val deviceCount = getDeviceCount()
        val userCount = getUserCount()
        val interactionCount = getInteractionCount()
        val eventCount = getEventsCount()
        val appInboxCount = getAppInboxMessagesCount()
        val recomEventsCount = getRecomEventsCount()

        val result = deviceCount == 0L
                && userCount == 0L
                && interactionCount == 0L
                && eventCount == 0L
                && appInboxCount == 0L
                && recomEventsCount == 0L
        /*@formatter:off*/ Logger.i(TAG, "isDatabaseEmpty(): ", "result = $result")
        /*@formatter:on*/
        return result
    }
    //==============================================================================================

    override fun insertDevice(device: DeviceDb) {
        contentValues.putDevice(device)
        database.insert(table = TABLE_NAME_DEVICE, contentValues = contentValues)
        contentValues.clear()
    }

    override fun getDevices(limit: Int?): List<DeviceDb> {
        val deviceEvents: MutableList<DeviceDb> = mutableListOf()

        var cursor: Cursor? = null
        try {
            cursor = database.query(
                table = TABLE_NAME_DEVICE,
                columns = DbSchema.DeviceSchema.getAllColumns(),
                orderBy = "$COLUMN_TIMESTAMP ASC",
                limit = limit?.toString()
            )
            while (cursor.moveToNext()) {
                val timestamp = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_TIMESTAMP))
                val device = cursor.getDevice()

                if (device != null) {
                    deviceEvents.add(device)
                } else {
                    val rowId = cursor.getLongOrNull(cursor.getColumnIndex(COLUMN_DEVICE_ROW_ID))
                    val exception =
                        SQLException("Unable to read data from SQL database. timeStamp=$timestamp, device=$device")
                    if (rowId == null) {
                        /*@formatter:off*/ Logger.e(TAG, "getDevices(). rowId is NULL ", exception)
                        /*@formatter:on*/
                    } else {
                        database.delete(
                            table = TABLE_NAME_DEVICE,
                            whereClause = "$COLUMN_DEVICE_ROW_ID=?",
                            whereArgs = arrayOf(rowId.toString())
                        )
                        /*@formatter:off*/ Logger.e(TAG, "getDevices(). Removed invalid entry from database. device=$device ", exception)
                        /*@formatter:on*/
                    }
                }
            }
        } catch (t: Throwable) {
            handleSQLiteError("Unable to get events from the table.", t)
        } finally {
            cursor?.close()
        }
        return deviceEvents
    }

    override fun getDeviceCount(): Long = database.getRowCount(TABLE_NAME_DEVICE)

    override fun deleteDevices(count: Int, oldest: Boolean) {
        val order = if (oldest) "ASC" else "DESC"
        database.delete(
            table = TABLE_NAME_DEVICE,
            whereClause = "$COLUMN_DEVICE_ROW_ID in (select $COLUMN_DEVICE_ROW_ID from $TABLE_NAME_DEVICE ORDER BY $COLUMN_TIMESTAMP $order LIMIT $count)"
        )
    }


    //==============================================================================================
    override fun insertUser(user: UserDb) {
        contentValues.putUser(user)
        val rowId = database.insert(table = TABLE_NAME_USER, contentValues = contentValues)
        contentValues.clear()

        user.userAttributes?.let { userAttrs ->
            contentValues.putUserAttributes(rowId, userAttrs)
            database.insert(table = TABLE_NAME_USER_ATTRIBUTES, contentValues = contentValues)
            contentValues.clear()

            userAttrs.address?.let { userAddress ->
                contentValues.putUserAddress(rowId, userAddress)
                database.insert(table = TABLE_NAME_USER_ADDRESS, contentValues = contentValues)
                contentValues.clear()
            }
        }
    }

    override fun getUser(limit: Int?): List<UserDb> {
        val userEvents: MutableList<UserDb> = mutableListOf()
        val rawQueryLimit: String = limit?.let { " LIMIT $it" } ?: ""

        var cursor: Cursor? = null
        try {
            val rawQuery = "SELECT" +
                    "  $TABLE_NAME_USER.$COLUMN_DEVICE_ID AS $COLUMN_DEVICE_ID," +
                    "  $TABLE_NAME_USER.$COLUMN_EXTERNAL_USER_ID AS $COLUMN_EXTERNAL_USER_ID," +
                    "  $TABLE_NAME_USER.$COLUMN_TIMESTAMP AS $COLUMN_TIMESTAMP," +
                    "  $TABLE_NAME_USER.$COLUMN_SUBSCRIPTION_KEYS AS $COLUMN_SUBSCRIPTION_KEYS," +
                    "  $TABLE_NAME_USER.$COLUMN_GROUP_NAMES_INCLUDE AS $COLUMN_GROUP_NAMES_INCLUDE," +
                    "  $TABLE_NAME_USER.$COLUMN_GROUP_NAMES_EXCLUDE AS $COLUMN_GROUP_NAMES_EXCLUDE," +
                    "  $TABLE_NAME_USER_ATTRIBUTES.$COLUMN_PHONE AS $COLUMN_PHONE," +
                    "  $TABLE_NAME_USER_ATTRIBUTES.$COLUMN_EMAIL AS $COLUMN_EMAIL," +
                    "  $TABLE_NAME_USER_ATTRIBUTES.$COLUMN_FIRST_NAME AS $COLUMN_FIRST_NAME," +
                    "  $TABLE_NAME_USER_ATTRIBUTES.$COLUMN_LAST_NAME AS $COLUMN_LAST_NAME," +
                    "  $TABLE_NAME_USER_ATTRIBUTES.$COLUMN_LANGUAGE_CODE AS $COLUMN_LANGUAGE_CODE," +
                    "  $TABLE_NAME_USER_ATTRIBUTES.$COLUMN_TIME_ZONE AS $COLUMN_TIME_ZONE," +
                    "  $TABLE_NAME_USER_ATTRIBUTES.$COLUMN_CUSTOM_FIELDS AS $COLUMN_CUSTOM_FIELDS," +
                    "  $TABLE_NAME_USER_ADDRESS.$COLUMN_REGION AS $COLUMN_REGION," +
                    "  $TABLE_NAME_USER_ADDRESS.$COLUMN_TOWN AS $COLUMN_TOWN," +
                    "  $TABLE_NAME_USER_ADDRESS.$COLUMN_ADDRESS AS $COLUMN_ADDRESS," +
                    "  $TABLE_NAME_USER_ADDRESS.$COLUMN_POSTCODE AS $COLUMN_POSTCODE" +
                    " FROM $TABLE_NAME_USER" +
                    "  LEFT JOIN $TABLE_NAME_USER_ATTRIBUTES ON $TABLE_NAME_USER.$COLUMN_USER_ROW_ID = $TABLE_NAME_USER_ATTRIBUTES.$COLUMN_USER_ROW_ID" +
                    "  LEFT JOIN $TABLE_NAME_USER_ADDRESS ON $TABLE_NAME_USER.$COLUMN_USER_ROW_ID = $TABLE_NAME_USER_ADDRESS.$COLUMN_USER_ROW_ID" +
                    rawQueryLimit
            cursor = database.rawQuery(rawQuery)
            while (cursor.moveToNext()) {
                val timestamp = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_TIMESTAMP))
                val user = cursor.getUser()

                if (user != null) {
                    userEvents.add(user)
                } else {
                    val rowId = cursor.getLongOrNull(cursor.getColumnIndex(COLUMN_USER_ROW_ID))
                    val exception =
                        SQLException("Unable to read data from SQL database. timeStamp=$timestamp, user=$user")
                    if (rowId == null) {
                        /*@formatter:off*/ Logger.e(TAG, "getUser(). rowId is NULL ", exception)
                        /*@formatter:on*/
                    } else {
                        database.delete(
                            table = TABLE_NAME_USER,
                            whereClause = "$COLUMN_USER_ROW_ID=?",
                            whereArgs = arrayOf(rowId.toString())
                        )
                        /*@formatter:off*/ Logger.e(TAG, "getUser(). Removed invalid entry from database. user=$user ", exception)
                        /*@formatter:on*/
                    }
                }
            }
        } catch (t: Throwable) {
            handleSQLiteError("Unable to get events from the table.", t)
        } finally {
            cursor?.close()
        }
        return userEvents
    }

    override fun getUserCount(): Long = database.getRowCount(TABLE_NAME_USER)

    override fun deleteUsers(count: Int, oldest: Boolean) {
        val order = if (oldest) "ASC" else "DESC"
        database.delete(
            table = TABLE_NAME_USER,
            whereClause = "$COLUMN_USER_ROW_ID in (select $COLUMN_USER_ROW_ID from $TABLE_NAME_USER ORDER BY $COLUMN_TIMESTAMP $order LIMIT $count)"
        )
    }

    //==============================================================================================
    override fun insertInteraction(interaction: InteractionDb) {
        contentValues.putInteraction(interaction)
        database.insert(table = TABLE_NAME_INTERACTION, contentValues = contentValues)
        contentValues.clear()
    }

    override fun getInteractions(limit: Int?): List<InteractionDb> {
        val interactionEvents: MutableList<InteractionDb> = mutableListOf()

        var cursor: Cursor? = null
        try {
            cursor = database.query(
                table = TABLE_NAME_INTERACTION,
                columns = DbSchema.InteractionSchema.getAllColumns(),
                orderBy = "$COLUMN_TIMESTAMP ASC",
                limit = limit?.toString()
            )
            while (cursor.moveToNext()) {
                val timestamp = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_TIMESTAMP))
                val interaction = cursor.getInteraction()

                if (interaction != null) {
                    interactionEvents.add(interaction)
                } else {
                    val rowId =
                        cursor.getLongOrNull(cursor.getColumnIndex(COLUMN_INTERACTION_ROW_ID))
                    val exception =
                        SQLException("Unable to read data from SQL database. timeStamp=$timestamp, interaction=$interaction")
                    if (rowId == null) {
                        /*@formatter:off*/ Logger.e(TAG, "getInteractions(). rowId is NULL ", exception)
                        /*@formatter:on*/
                    } else {
                        database.delete(
                            table = TABLE_NAME_INTERACTION,
                            whereClause = "$COLUMN_INTERACTION_ROW_ID=?",
                            whereArgs = arrayOf(rowId.toString())
                        )
                        /*@formatter:off*/ Logger.e(TAG, "getInteractions(). Removed invalid entry from database. interaction=$interaction ", exception)
                        /*@formatter:on*/
                    }
                }
            }
        } catch (t: Throwable) {
            handleSQLiteError("Unable to get events from the table.", t)
        } finally {
            cursor?.close()
        }
        return interactionEvents
    }

    override fun getInteractionCount(): Long = database.getRowCount(TABLE_NAME_INTERACTION)

    override fun deleteInteractions(count: Int, oldest: Boolean) {
        val order = if (oldest) "ASC" else "DESC"
        database.delete(
            table = TABLE_NAME_INTERACTION,
            whereClause = "$COLUMN_INTERACTION_ROW_ID in (select $COLUMN_INTERACTION_ROW_ID from $TABLE_NAME_INTERACTION ORDER BY $COLUMN_TIMESTAMP $order LIMIT $count)"
        )
    }

    override fun deleteInteractionByTime(outdatedTime: String): Int {
        return database.delete(
            table = TABLE_NAME_INTERACTION,
            whereClause = "$COLUMN_INTERACTION_TIME < '$outdatedTime'"
        )
    }

    //==============================================================================================
    override fun insertEvents(events: EventsDb) {
        var parentRowId: Long = -1L

        var cursor: Cursor? = null
        try {
            cursor = if (events.externalUserId == null) {
                database.query(
                    table = TABLE_NAME_EVENTS,
                    columns = DbSchema.EventsSchema.getAllColumns(),
                    selection = "$COLUMN_EVENTS_DEVICE_ID=? AND $COLUMN_EVENTS_EXTERNAL_USER_ID IS NULL",
                    selectionArgs = arrayOf(events.deviceId)
                )
            } else {
                database.query(
                    table = TABLE_NAME_EVENTS,
                    columns = DbSchema.EventsSchema.getAllColumns(),
                    selection = "$COLUMN_EVENTS_DEVICE_ID=? AND $COLUMN_EVENTS_EXTERNAL_USER_ID=?",
                    selectionArgs = arrayOf(events.deviceId, events.externalUserId)
                )
            }

            if (cursor.moveToFirst()) {
                cursor.getLongOrNull(cursor.getColumnIndex(COLUMN_EVENTS_ID))?.let {
                    parentRowId = it
                }
            }
        } catch (t: Throwable) {
            handleSQLiteError("Unable to get events from the table.", t)
        } finally {
            cursor?.close()
        }

        if (parentRowId == -1L) {
            contentValues.putEvents(events)
            parentRowId = database.insert(table = TABLE_NAME_EVENTS, contentValues = contentValues)
            contentValues.clear()
        }

        val eventListContentValues = events.eventList.toContentValuesList(parentRowId)
        database.insertMultiple(table = TABLE_NAME_EVENT, contentValues = eventListContentValues)
    }

    override fun getEvents(limit: Int?): List<EventsDb> {
        var cursor: Cursor? = null

        val eventsParentTableList: MutableMap<String, EventsDb> = mutableMapOf()
        try {
            cursor = database.query(
                table = TABLE_NAME_EVENTS,
                columns = DbSchema.EventsSchema.getAllColumns()
            )
            while (cursor.moveToNext()) {
                val eventsId = cursor.getLongOrNull(cursor.getColumnIndex(DbSchema.EventsSchema.COLUMN_EVENTS_ID))
                val deviceId = cursor.getStringOrNull(cursor.getColumnIndex(DbSchema.EventsSchema.COLUMN_EVENTS_DEVICE_ID))
                val externalUserId = cursor.getStringOrNull(cursor.getColumnIndex(DbSchema.EventsSchema.COLUMN_EVENTS_EXTERNAL_USER_ID))

                if (allElementsNotNull(eventsId, deviceId)) {
                    eventsParentTableList[eventsId!!.toString()] =
                        EventsDb(deviceId!!, externalUserId, listOf())
                } else {
                    val exception =
                        SQLException("Unable to read data from SQL database getEvents(). eventsId=$eventsId, deviceId=$deviceId, externalUserId=$externalUserId")
                    if (eventsId == null) {
                        /*@formatter:off*/ Logger.e(TAG, "getEvents(). rowId is NULL ", exception)
                        /*@formatter:on*/
                    } else {
                        database.delete(
                            table = TABLE_NAME_EVENTS,
                            whereClause = "$COLUMN_EVENTS_ID=?",
                            whereArgs = arrayOf(eventsId.toString())
                        )
                        /*@formatter:off*/ Logger.e(TAG, "getEvents(). Removed invalid entry from database. eventsId=$eventsId, deviceId=$deviceId, externalUserId=$externalUserId", exception)
                        /*@formatter:on*/
                    }
                }
            }
        } catch (t: Throwable) {
            handleSQLiteError("Unable to get events from the table.", t)
        } finally {
            cursor?.close()
        }

        val eventsResult: MutableList<EventsDb> = mutableListOf()
        for (eventsParent in eventsParentTableList.entries.iterator()) {
            val foreignKeyRowId = eventsParent.key
            val eventList: MutableList<EventDb> = mutableListOf()

            var cursorChild: Cursor? = null
            try {
                cursorChild = database.query(
                    table = TABLE_NAME_EVENT,
                    columns = DbSchema.EventSchema.getAllColumns(),
                    selection = "$COLUMN_EVENTS_ID=?",
                    selectionArgs = arrayOf(foreignKeyRowId),
                    limit = limit?.toString()
                )

                while (cursorChild.moveToNext()) {
                    val event = cursorChild.getEvent()
                    if (event != null) {
                        eventList.add(event)
                    } else {
                        val rowId = cursorChild.getLongOrNull(
                            cursorChild.getColumnIndex(COLUMN_EVENT_ROW_ID)
                        )
                        val exception =
                            SQLException("Unable to read data from SQL database. event=$event")
                        if (rowId == null) {
                            /*@formatter:off*/ Logger.e(TAG, "getEvents(). rowId is NULL ", exception)
                            /*@formatter:on*/
                        } else {
                            database.delete(
                                table = TABLE_NAME_EVENT,
                                whereClause = "$COLUMN_EVENT_ROW_ID=?",
                                whereArgs = arrayOf(rowId.toString())
                            )
                            /*@formatter:off*/ Logger.e(TAG, "getEvents(). Removed invalid entry from database. event=$event ", exception)
                            /*@formatter:on*/
                        }
                    }
                }
            } catch (t: Throwable) {
                handleSQLiteError("Unable to get events from the table.", t)
            } finally {
                cursorChild?.close()
            }


            if (eventList.isNotEmpty()) {
                val singleEventResult = EventsDb(
                    eventsParent.value.deviceId,
                    eventsParent.value.externalUserId,
                    eventList
                )
                eventsResult.add(singleEventResult)
            }
        }

        database.cleanUnlinkedEvents()

        return eventsResult
    }

    override fun getEventsCount(): Long = database.getRowCount(TABLE_NAME_EVENT)

    /**
     * Call [com.reteno.core.data.local.database.RetenoDatabase.cleanUnlinkedEvents] each time you remove events from Event table (Child table)
     */
    override fun deleteEvents(count: Int, oldest: Boolean) {
        val order = if (oldest) "ASC" else "DESC"
        database.delete(
            table = TABLE_NAME_EVENT,
            whereClause = "$COLUMN_EVENT_ROW_ID in (select $COLUMN_EVENT_ROW_ID from $TABLE_NAME_EVENT ORDER BY $COLUMN_EVENT_OCCURRED $order LIMIT $count)"
        )

        database.cleanUnlinkedEvents()
    }

    override fun deleteEventsByTime(outdatedTime: String): Int {
        val count = database.delete(
            table = TABLE_NAME_EVENT,
            whereClause = "$COLUMN_EVENT_OCCURRED < '$outdatedTime'"
        )
        database.cleanUnlinkedEvents()
        return count
    }


    //==============================================================================================
    /** AppInbox **/
    override fun insertAppInboxMessage(message: AppInboxMessageDb) {
        contentValues.putAppInbox(message)
        database.insert(table = TABLE_NAME_APP_INBOX, contentValues = contentValues)
        contentValues.clear()
    }

    override fun getAppInboxMessages(limit: Int?): List<AppInboxMessageDb> {
        val inboxMessage: MutableList<AppInboxMessageDb> = mutableListOf()

        var cursor: Cursor? = null
        try {
            cursor = database.query(
                table = TABLE_NAME_APP_INBOX,
                columns = DbSchema.AppInboxSchema.getAllColumns(),
                orderBy = "$COLUMN_APP_INBOX_TIME ASC",
                limit = limit?.toString()
            )
            while (cursor.moveToNext()) {
                val timestamp = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_APP_INBOX_TIME))
                val inbox = cursor.getAppInbox()

                if (inbox != null) {
                    inboxMessage.add(inbox)
                } else {
                    val rowId =
                        cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_APP_INBOX_ID))
                    val exception =
                        SQLException("Unable to read data from SQL database. timeStamp=$timestamp, inboxMessage=null. rowId = $rowId")
                    if (rowId == null) {
                        /*@formatter:off*/ Logger.e(TAG, "getAppInboxMessages(). rowId is NULL ", exception)
                        /*@formatter:on*/
                    } else {
                        database.delete(
                            table = TABLE_NAME_APP_INBOX,
                            whereClause = "$COLUMN_APP_INBOX_ID=?",
                            whereArgs = arrayOf(rowId.toString())
                        )
                        /*@formatter:off*/ Logger.e(TAG, "getAppInboxMessages(). Removed invalid entry from database. inboxMessage=null, rowId = $rowId", exception)
                        /*@formatter:on*/
                    }
                }
            }
        } catch (t: Throwable) {
            handleSQLiteError("Unable to get AppInboxMessage from the table.", t)
        } finally {
            cursor?.close()
        }
        return inboxMessage
    }

    override fun getAppInboxMessagesCount(): Long = database.getRowCount(TABLE_NAME_APP_INBOX)

    override fun deleteAppInboxMessages(count: Int, oldest: Boolean) {
        val order = if (oldest) "ASC" else "DESC"
        database.delete(
            table = TABLE_NAME_APP_INBOX,
            whereClause = "$COLUMN_APP_INBOX_ID in (select $COLUMN_APP_INBOX_ID from $TABLE_NAME_APP_INBOX ORDER BY $COLUMN_APP_INBOX_TIME $order LIMIT $count)"
        )
    }

    override fun deleteAllAppInboxMessages() {
        database.delete(table = TABLE_NAME_APP_INBOX)
    }

    override fun deleteAppInboxMessagesByTime(outdatedTime: String): Int {
        return database.delete(
            table = TABLE_NAME_APP_INBOX,
            whereClause = "$COLUMN_APP_INBOX_TIME < '$outdatedTime'"
        )
    }

    //==============================================================================================
    override fun insertRecomEvents(recomEvents: RecomEventsDb) {
        if (recomEvents.recomEvents == null || recomEvents.recomEvents.isEmpty()) {
            /*@formatter:off*/ Logger.e(TAG, "insertRecomEvents(): ", Throwable("recomEvents = $recomEvents"))
            /*@formatter:on*/
            return
        }

        val recomVariantId: String? =
            if (isRecomVariantIdPresentInParentTable(recomEvents.recomVariantId)) {
                recomEvents.recomVariantId
            } else {
                if (putRecomVariantIdToParentTable(recomEvents.recomVariantId)) {
                    recomEvents.recomVariantId
                } else {
                    null
                }
            }

        recomVariantId?.let { variantId ->
            insertRecomEventList(variantId, recomEvents.recomEvents)
        }
    }

    private fun isRecomVariantIdPresentInParentTable(recomVariantId: String): Boolean {
        var cursor: Cursor? = null

        try {
            cursor = database.query(
                table = TABLE_NAME_RECOM_EVENTS,
                columns = DbSchema.RecomEventsSchema.getAllColumns(),
                selection = "$COLUMN_RECOM_VARIANT_ID=?",
                selectionArgs = arrayOf(recomVariantId)
            )

            if (cursor.moveToFirst()) {
                return true
            }
        } catch (t: Throwable) {
            handleSQLiteError("Unable to get recomEvents from the table.", t)
        } finally {
            cursor?.close()
        }

        return false
    }

    private fun putRecomVariantIdToParentTable(recomVariantId: String): Boolean {
        contentValues.putRecomVariantId(recomVariantId)
        val parentRowId = database.insert(table = TABLE_NAME_RECOM_EVENTS, contentValues = contentValues)
        contentValues.clear()

        return parentRowId != -1L
    }

    private fun insertRecomEventList(
        variantId: String,
        recomEventListDb: List<RecomEventDb>) {

        val contentValues = recomEventListDb.toContentValuesList(variantId)
        database.insertMultiple(
            table = TABLE_NAME_RECOM_EVENT,
            contentValues = contentValues
        )
    }

    override fun getRecomEvents(limit: Int?): List<RecomEventsDb> {
        val recomVariantIds: MutableList<String> = readRecomVariantIds()
        val recomEventsResult: MutableList<RecomEventsDb> = readRecomEventList(recomVariantIds, limit)

        database.cleanUnlinkedRecomEvents()

        return recomEventsResult
    }

    private fun readRecomVariantIds(): MutableList<String> {
        val recomVariantIds: MutableList<String> = mutableListOf()
        var cursor: Cursor? = null
        try {
            cursor = database.query(
                table = TABLE_NAME_RECOM_EVENTS,
                columns = DbSchema.RecomEventsSchema.getAllColumns()
            )
            while (cursor.moveToNext()) {
                val recomVariantId =
                    cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_RECOM_VARIANT_ID))

                if (recomVariantId != null) {
                    recomVariantIds.add(recomVariantId)
                } else {
                    val exception =
                        SQLException("Unable to read data from SQL database. recomVariantId=null")
                    /*@formatter:off*/ Logger.e(TAG, "Error reading database. recomVariantId=$recomVariantId", exception)
                    /*@formatter:on*/
                }
            }
        } catch (t: Throwable) {
            handleSQLiteError("Unable to get recomEvents from the table.", t)
        } finally {
            cursor?.close()
        }

        return recomVariantIds
    }

    private fun readRecomEventList(recomVariantIds: MutableList<String>, limit: Int?): MutableList<RecomEventsDb> {
        val recomEventsResult: MutableList<RecomEventsDb> = mutableListOf()

        for (recomVariantId in recomVariantIds) {
            val recomEvents: MutableList<RecomEventDb> = mutableListOf()

            var cursorChild: Cursor? = null
            try {
                cursorChild = database.query(
                    table = TABLE_NAME_RECOM_EVENT,
                    columns = DbSchema.RecomEventSchema.getAllColumns(),
                    selection = "$COLUMN_RECOM_VARIANT_ID=?",
                    selectionArgs = arrayOf(recomVariantId),
                    limit = limit?.toString()
                )

                while (cursorChild.moveToNext()) {
                    val recomEvent = cursorChild.getRecomEvent()

                    if (recomEvent != null) {
                        recomEvents.add(recomEvent)
                    } else {
                        val rowId = cursorChild.getLongOrNull(
                            cursorChild.getColumnIndex(COLUMN_RECOM_EVENT_ROW_ID)
                        )
                        val exception =
                            SQLException("Unable to read data from SQL database. recomEvent=$recomEvent")
                        if (rowId == null) {
                            /*@formatter:off*/ Logger.e(TAG, "getRecomEvents(). rowId is NULL ", exception)
                            /*@formatter:on*/
                        } else {
                            database.delete(
                                table = TABLE_NAME_RECOM_EVENT,
                                whereClause = "$COLUMN_RECOM_EVENT_ROW_ID=?",
                                whereArgs = arrayOf(rowId.toString())
                            )
                            /*@formatter:off*/ Logger.e(TAG, "getRecomEvents(). Removed invalid entry from database. recomEvent=$recomEvent ", exception)
                            /*@formatter:on*/
                        }
                    }
                }
            } catch (t: Throwable) {
                handleSQLiteError("Unable to get events from the table.", t)
            } finally {
                cursorChild?.close()
            }

            recomEventsResult.add(
                RecomEventsDb(
                    recomVariantId = recomVariantId.toString(),
                    recomEvents = recomEvents
                )
            )
        }
        return recomEventsResult
    }

    override fun getRecomEventsCount(): Long = database.getRowCount(TABLE_NAME_RECOM_EVENT)

    /**
     * Call [com.reteno.core.data.local.database.RetenoDatabase.cleanUnlinkedRecomEvents] each time you remove events from RecomEvent table (Child table)
     */
    override fun deleteRecomEvents(count: Int, oldest: Boolean) {
        val order = if (oldest) "ASC" else "DESC"
        database.delete(
            table = TABLE_NAME_RECOM_EVENT,
            whereClause = "$COLUMN_RECOM_EVENT_ROW_ID in (select $COLUMN_RECOM_EVENT_ROW_ID from $TABLE_NAME_RECOM_EVENT ORDER BY $COLUMN_RECOM_EVENT_OCCURRED $order LIMIT $count)"
        )

        database.cleanUnlinkedRecomEvents()
    }

    override fun deleteRecomEventsByTime(outdatedTime: String): Int {
        val count = database.delete(
            table = TABLE_NAME_RECOM_EVENT,
            whereClause = "$COLUMN_RECOM_EVENT_OCCURRED < '$outdatedTime'"
        )
        database.cleanUnlinkedRecomEvents()
        return count
    }

    //==============================================================================================
    private fun handleSQLiteError(log: String, t: Throwable) {
        /*@formatter:off*/ Logger.e(TAG, "handleSQLiteError(): $log", t)
        /*@formatter:on*/
    }

    companion object {
        val TAG: String = RetenoDatabaseManagerImpl::class.java.simpleName
    }
}