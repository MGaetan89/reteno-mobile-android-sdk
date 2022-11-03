package com.reteno.core.data.local.database

import android.content.ContentValues
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.reteno.core.RetenoImpl
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
import com.reteno.core.data.local.database.DbSchema.InteractionSchema.TABLE_NAME_INTERACTION
import com.reteno.core.data.local.database.DbSchema.UserAddressSchema.COLUMN_ADDRESS
import com.reteno.core.data.local.database.DbSchema.UserAddressSchema.COLUMN_POSTCODE
import com.reteno.core.data.local.database.DbSchema.UserAddressSchema.COLUMN_REGION
import com.reteno.core.data.local.database.DbSchema.UserAddressSchema.COLUMN_TOWN
import com.reteno.core.data.local.database.DbSchema.UserAddressSchema.TABLE_NAME_USER_ADDRESS
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_EMAIL
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_FIRST_NAME
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_LANGUAGE_CODE
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_LAST_NAME
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_PHONE
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_TIME_CUSTOM_FIELDS
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.COLUMN_TIME_ZONE
import com.reteno.core.data.local.database.DbSchema.UserAttributesSchema.TABLE_NAME_USER_ATTRIBUTES
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_DEVICE_ID
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_EXTERNAL_USER_ID
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_GROUP_NAMES_EXCLUDE
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_GROUP_NAMES_INCLUDE
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_SUBSCRIPTION_KEYS
import com.reteno.core.data.local.database.DbSchema.UserSchema.COLUMN_USER_ROW_ID
import com.reteno.core.data.local.database.DbSchema.UserSchema.TABLE_NAME_USER
import com.reteno.core.data.local.database.DbUtil.getDevice
import com.reteno.core.data.local.database.DbUtil.getEvents
import com.reteno.core.data.local.database.DbUtil.getInteraction
import com.reteno.core.data.local.database.DbUtil.getUser
import com.reteno.core.data.local.database.DbUtil.putDevice
import com.reteno.core.data.local.database.DbUtil.putEvents
import com.reteno.core.data.local.database.DbUtil.putInteraction
import com.reteno.core.data.local.database.DbUtil.putUser
import com.reteno.core.data.local.database.DbUtil.putUserAddress
import com.reteno.core.data.local.database.DbUtil.putUserAttributes
import com.reteno.core.data.local.database.DbUtil.toContentValuesList
import com.reteno.core.data.local.model.InteractionModelDb
import com.reteno.core.data.remote.model.user.UserDTO
import com.reteno.core.model.Event
import com.reteno.core.model.Events
import com.reteno.core.model.device.Device
import com.reteno.core.util.Logger
import com.reteno.core.util.allElementsNotNull
import net.sqlcipher.Cursor
import net.sqlcipher.SQLException
import net.sqlcipher.database.SQLiteDatabase

class RetenoDatabaseManagerImpl : RetenoDatabaseManager {

    private val databaseManager: RetenoDatabaseImpl by lazy {
        SQLiteDatabase.loadLibs(RetenoImpl.application)
        RetenoDatabaseImpl(RetenoImpl.application)
    }
    private val contentValues = ContentValues()


    override fun insertDevice(device: Device) {
        contentValues.putDevice(device)
        databaseManager.insert(TABLE_NAME_DEVICE, null, contentValues)
        contentValues.clear()
    }

    override fun getDevices(limit: Int?): List<Device> {
        val deviceEvents: MutableList<Device> = mutableListOf()

        var cursor: Cursor? = null
        try {
            cursor = databaseManager.query(
                TABLE_NAME_DEVICE,
                DbSchema.DeviceSchema.getAllColumns(),
                null,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP ASC",
                limit?.toString()
            )
            while (cursor.moveToNext()) {
                val timestamp = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_TIMESTAMP))
                val device = cursor.getDevice()

                if (device != null) {
                    deviceEvents.add(device)
                } else {
                    val rowId = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_DEVICE_ROW_ID))
                    val exception =
                        SQLException("Unable to read data from SQL database. timeStamp=$timestamp, device=$device")
                    if (rowId == null) {
                        /*@formatter:off*/ Logger.e(TAG, "getDevices(). rowId is NULL ", exception)
                        /*@formatter:on*/
                    } else {
                        databaseManager.delete(
                            TABLE_NAME_DEVICE,
                            "$COLUMN_DEVICE_ROW_ID=?",
                            arrayOf(rowId)
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

    override fun getDeviceCount(): Long = databaseManager.getRowCount(TABLE_NAME_DEVICE)

    override fun deleteDevices(count: Int, oldest: Boolean) {
        val order = if (oldest) "ASC" else "DESC"
        databaseManager.delete(
            TABLE_NAME_DEVICE,
            "$COLUMN_DEVICE_ROW_ID in (select $COLUMN_DEVICE_ROW_ID from $TABLE_NAME_DEVICE ORDER BY $COLUMN_TIMESTAMP $order LIMIT $count)",
            null
        )
    }


    //==============================================================================================
    override fun insertUser(user: UserDTO) {
        contentValues.putUser(user)
        val rowId = databaseManager.insert(TABLE_NAME_USER, null, contentValues)
        contentValues.clear()

        user.userAttributes?.let { userAttrs ->
            contentValues.putUserAttributes(rowId, userAttrs)
            databaseManager.insert(TABLE_NAME_USER_ATTRIBUTES, null, contentValues)
            contentValues.clear()

            userAttrs.address?.let { userAddress ->
                contentValues.putUserAddress(rowId, userAddress)
                databaseManager.insert(TABLE_NAME_USER_ADDRESS, null, contentValues)
                contentValues.clear()
            }
        }
    }

    override fun getUser(limit: Int?): List<UserDTO> {
        val userEvents: MutableList<UserDTO> = mutableListOf()
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
                    "  $TABLE_NAME_USER_ATTRIBUTES.$COLUMN_TIME_CUSTOM_FIELDS AS $COLUMN_TIME_CUSTOM_FIELDS," +
                    "  $TABLE_NAME_USER_ADDRESS.$COLUMN_REGION AS $COLUMN_REGION," +
                    "  $TABLE_NAME_USER_ADDRESS.$COLUMN_TOWN AS $COLUMN_TOWN," +
                    "  $TABLE_NAME_USER_ADDRESS.$COLUMN_ADDRESS AS $COLUMN_ADDRESS," +
                    "  $TABLE_NAME_USER_ADDRESS.$COLUMN_POSTCODE AS $COLUMN_POSTCODE" +
                    " FROM $TABLE_NAME_USER" +
                    "  LEFT JOIN $TABLE_NAME_USER_ATTRIBUTES ON $TABLE_NAME_USER.$COLUMN_USER_ROW_ID = $TABLE_NAME_USER_ATTRIBUTES.$COLUMN_USER_ROW_ID" +
                    "  LEFT JOIN $TABLE_NAME_USER_ADDRESS ON $TABLE_NAME_USER.$COLUMN_USER_ROW_ID = $TABLE_NAME_USER_ADDRESS.$COLUMN_USER_ROW_ID" +
                    rawQueryLimit
            cursor = databaseManager.rawQuery(rawQuery, null)
            while (cursor.moveToNext()) {
                val timestamp = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_TIMESTAMP))
                val user = cursor.getUser()

                if (user != null) {
                    userEvents.add(user)
                } else {
                    val rowId = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_USER_ROW_ID))
                    val exception =
                        SQLException("Unable to read data from SQL database. timeStamp=$timestamp, user=$user")
                    if (rowId == null) {
                        /*@formatter:off*/ Logger.e(TAG, "getUser(). rowId is NULL ", exception)
                        /*@formatter:on*/
                    } else {
                        databaseManager.delete(
                            TABLE_NAME_USER,
                            "$COLUMN_USER_ROW_ID=?",
                            arrayOf(rowId)
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

    override fun getUserCount(): Long = databaseManager.getRowCount(TABLE_NAME_USER)

    override fun deleteUsers(count: Int, oldest: Boolean) {
        val order = if (oldest) "ASC" else "DESC"
        databaseManager.delete(
            TABLE_NAME_USER,
            "$COLUMN_USER_ROW_ID in (select $COLUMN_USER_ROW_ID from $TABLE_NAME_USER ORDER BY $COLUMN_TIMESTAMP $order LIMIT $count)",
            null
        )
    }

    //==============================================================================================
    override fun insertInteraction(interaction: InteractionModelDb) {
        contentValues.putInteraction(interaction)
        databaseManager.insert(TABLE_NAME_INTERACTION, null, contentValues)
        contentValues.clear()
    }

    override fun getInteractions(limit: Int?): List<InteractionModelDb> {
        val interactionEvents: MutableList<InteractionModelDb> = mutableListOf()

        var cursor: Cursor? = null
        try {
            cursor = databaseManager.query(
                TABLE_NAME_INTERACTION,
                DbSchema.InteractionSchema.getAllColumns(),
                null,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP ASC",
                limit?.toString()
            )
            while (cursor.moveToNext()) {
                val timestamp = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_TIMESTAMP))
                val interaction = cursor.getInteraction()

                if (interaction != null) {
                    interactionEvents.add(interaction)
                } else {
                    val rowId =
                        cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_INTERACTION_ROW_ID))
                    val exception =
                        SQLException("Unable to read data from SQL database. timeStamp=$timestamp, interaction=$interaction")
                    if (rowId == null) {
                        /*@formatter:off*/ Logger.e(TAG, "getInteractions(). rowId is NULL ", exception)
                        /*@formatter:on*/
                    } else {
                        databaseManager.delete(
                            TABLE_NAME_INTERACTION,
                            "$COLUMN_INTERACTION_ROW_ID=?",
                            arrayOf(rowId)
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

    override fun getInteractionCount(): Long = databaseManager.getRowCount(TABLE_NAME_INTERACTION)

    override fun deleteInteractions(count: Int, oldest: Boolean) {
        val order = if (oldest) "ASC" else "DESC"
        databaseManager.delete(
            TABLE_NAME_INTERACTION,
            "$COLUMN_INTERACTION_ROW_ID in (select $COLUMN_INTERACTION_ROW_ID from $TABLE_NAME_INTERACTION ORDER BY $COLUMN_TIMESTAMP $order LIMIT $count)",
            null
        )
    }

    override fun insertEvents(events: Events) {
        var parentRowId: Long = -1L

        var cursor: Cursor? = null
        try {
            cursor = databaseManager.query(
                TABLE_NAME_EVENTS,
                DbSchema.EventsSchema.getAllColumns(),
                "$COLUMN_EVENTS_DEVICE_ID=? AND $COLUMN_EVENTS_EXTERNAL_USER_ID=?",
                arrayOf(events.deviceId, events.externalUserId ?: "NULL"),
                null,
                null,
                null
            )

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
            parentRowId = databaseManager.insert(TABLE_NAME_EVENTS, null, contentValues)
            contentValues.clear()
        }

        val eventListContentValues = events.eventList.toContentValuesList(parentRowId)
        databaseManager.insertMultiple(TABLE_NAME_EVENT, null, eventListContentValues)
    }

    override fun getEvents(limit: Int?): List<Events> {
        var cursor: Cursor? = null

        val eventsParentTableList: MutableMap<String, Events> = mutableMapOf<String, Events>()
        try {
            cursor = databaseManager.query(
                TABLE_NAME_EVENTS,
                DbSchema.EventsSchema.getAllColumns(),
                null,
                null,
                null,
                null,
                null,
                limit?.toString()
            )
            while (cursor.moveToNext()) {
                val eventsId =
                    cursor.getStringOrNull(cursor.getColumnIndex(DbSchema.EventsSchema.COLUMN_EVENTS_ID))
                val deviceId =
                    cursor.getStringOrNull(cursor.getColumnIndex(DbSchema.EventsSchema.COLUMN_EVENTS_DEVICE_ID))
                val externalUserId =
                    cursor.getStringOrNull(cursor.getColumnIndex(DbSchema.EventsSchema.COLUMN_EVENTS_EXTERNAL_USER_ID))

                if (allElementsNotNull(eventsId, deviceId, externalUserId)) {
                    eventsParentTableList[eventsId!!] =
                        Events(deviceId!!, externalUserId!!, listOf())
                } else {
                    val rowId = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_EVENTS_ID))
                    val exception =
                        SQLException("Unable to read data from SQL database getEvents(). eventsId=$eventsId, deviceId=$deviceId, externalUserId=$externalUserId")
                    if (rowId == null) {
                        /*@formatter:off*/ Logger.e(TAG, "getEvents(). rowId is NULL ", exception)
                        /*@formatter:on*/
                    } else {
                        databaseManager.delete(
                            TABLE_NAME_EVENTS,
                            "$COLUMN_EVENTS_ID=?",
                            arrayOf(rowId)
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

        val eventsResult: MutableList<Events> = mutableListOf()
        for (eventsParent in eventsParentTableList.entries.iterator()) {
            val foreignKeyRowId = eventsParent.key
            val eventList: MutableList<Event> = mutableListOf()

            var cursorChild: Cursor? = null
            try {
                cursorChild = databaseManager.query(
                    TABLE_NAME_EVENT,
                    DbSchema.EventSchema.getAllColumns(),
                    "$COLUMN_EVENTS_ID=?",
                    arrayOf(foreignKeyRowId),
                    null,
                    null,
                    null,
                    null
                )

                while (cursorChild.moveToNext()) {
                    val events = cursorChild.getEvents()
                    if (events != null) {
                        eventList.add(events)
                    } else {
                        val rowId = cursorChild.getStringOrNull(
                            cursorChild.getColumnIndex(COLUMN_EVENT_ROW_ID)
                        )
                        val exception =
                            SQLException("Unable to read data from SQL database. events=$events")
                        if (rowId == null) {
                            /*@formatter:off*/ Logger.e(TAG, "getEvents(). rowId is NULL ", exception)
                            /*@formatter:on*/
                        } else {
                            databaseManager.delete(
                                TABLE_NAME_EVENT,
                                "$COLUMN_EVENT_ROW_ID=?",
                                arrayOf(rowId)
                            )
                            /*@formatter:off*/ Logger.e(TAG, "getEvents(). Removed invalid entry from database. events=$events ", exception)
                            /*@formatter:on*/
                        }
                    }
                }
            } catch (t: Throwable) {
                handleSQLiteError("Unable to get events from the table.", t)
            } finally {
                cursorChild?.close()
            }

            val singleEventResult =
                Events(eventsParent.value.deviceId, eventsParent.value.externalUserId, eventList)
            eventsResult.add(singleEventResult)
        }

        return eventsResult
    }

    override fun getEventsCount(): Long = databaseManager.getRowCount(TABLE_NAME_EVENT)

    override fun deleteEvents(count: Int, oldest: Boolean) {
        val order = if (oldest) "ASC" else "DESC"
        databaseManager.delete(
            TABLE_NAME_EVENT,
            "$COLUMN_EVENT_ROW_ID in (select $COLUMN_EVENT_ROW_ID from $TABLE_NAME_EVENT ORDER BY $COLUMN_EVENT_OCCURRED $order LIMIT $count)",
            null
        )

        databaseManager.cleanEventsRowsInParentTableWithNoChildren()
    }

    private fun handleSQLiteError(log: String, t: Throwable) {
        /*@formatter:off*/ Logger.e(TAG, "handleSQLiteError(): $log", t)
        /*@formatter:on*/
    }


    companion object {
        val TAG: String = RetenoDatabaseManagerImpl::class.java.simpleName
    }
}