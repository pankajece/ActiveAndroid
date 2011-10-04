package com.activeandroid;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.activeandroid.annotation.Column;
import com.activeandroid.serializer.TypeSerializer;

@SuppressWarnings("unchecked")
public abstract class ActiveRecordBase<T> {
	////////////////////////////////////////////////////////////////////////////////
	// PRIVATE MEMBERS

	@Column(name = "Id")
	private Long mId = null;

	private ApplicationCache mApplicationCache = ApplicationCache.getInstance();
	private Context mContext;
	private String mTableName;

	////////////////////////////////////////////////////////////////////////////////
	// CONSTRUCTORS

	public ActiveRecordBase() {
		mContext = mApplicationCache.getContext();
		mTableName = ReflectionUtils.getTableName(getClass());

		mApplicationCache.addEntity(this);
	}

	////////////////////////////////////////////////////////////////////////////////
	// PUBLIC METHODS

	/**
	 * Gets the current object's record Id.
	 * @return Long the current object's record Id.
	 */
	public Long getId() {
		return mId;
	}

	/**
	 * Gets the context associated with the object.
	 * @return Context the context associated with the object.
	 */
	public Context getContext() {
		return mContext;
	}

	/**
	 * Gets the name of the database table associated with the object.
	 * @return String the name of the database table associated with the object.
	 */
	public String getTableName() {
		return mTableName;
	}

	// ###  OPERATIONAL METHODS

	/**
	 * Deletes the current object's record from the database. References to this object will be null.
	 */
	public void delete() {
		final SQLiteDatabase db = mApplicationCache.openDatabase();
		db.delete(mTableName, "Id=?", new String[] { getId().toString() });
		mApplicationCache.removeEntity(this);
	}

	/**
	 * Saves the current object as a record to the database. Will insert or update the record based on
	 * its current existence. 
	 */
	public void save() {
		final SQLiteDatabase db = mApplicationCache.openDatabase();
		final ContentValues values = new ContentValues();

		for (Field field : ReflectionUtils.getTableFields(this.getClass())) {
			final String fieldName = ReflectionUtils.getColumnName(field);
			Class<?> fieldType = field.getType();

			field.setAccessible(true);

			try {
				Object value = field.get(this);

				if (value != null) {
					final TypeSerializer typeSerializer = mApplicationCache.getParserForType(fieldType);
					if (typeSerializer != null) {
						// serialize data
						value = typeSerializer.serialize(value);
						// set new object type
						if (value != null) {
							fieldType = value.getClass();
						}
					}
				}

				// Try to order by highest use
				if (value == null) {
					values.putNull(fieldName);
				}
				// String
				else if (fieldType.equals(String.class)) {
					values.put(fieldName, value.toString());
				}
				// Boolean
				else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
					values.put(fieldName, (Boolean) value);
				}
				// Long
				else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
					values.put(fieldName, (Long) value);
				}
				// Integer
				else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
					values.put(fieldName, (Integer) value);
				}
				// Float
				else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) {
					values.put(fieldName, (Float) value);
				}
				// Double
				else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
					values.put(fieldName, (Double) value);
				}
				// Character
				else if (fieldType.equals(Character.class) || fieldType.equals(char.class)) {
					values.put(fieldName, value.toString());
				}
				else if (!fieldType.isPrimitive() && fieldType.getSuperclass() != null
						&& fieldType.getSuperclass().equals(ActiveRecordBase.class)) {

					final long entityId = ((ActiveRecordBase<?>) value).getId();
					values.put(fieldName, entityId);
				}
			}
			catch (IllegalArgumentException e) {
				Log.e(Params.LOGGING_TAG, e.getClass().getName() + ": " + e.getMessage());
			}
			catch (IllegalAccessException e) {
				Log.e(Params.LOGGING_TAG, e.getClass().getName() + ": " + e.getMessage());
			}
		}

		if (mId == null) {
			mId = db.insert(mTableName, null, values);
		}
		else {
			db.update(mTableName, values, "Id=" + mId, null);
		}

		//mApplication.closeDatabase();
	}

	// ###  RELATIONAL METHODS

	/**
	 * Retrieves related entities on a field on the object.
	 * 
	 * @param type the type of this object.
	 * @param through the field on the other object through which this object is related.
	 * @return ArrayList<E> ArrayList of objects returned by the query.
	 */
	protected <E> ArrayList<E> getMany(Class<? extends ActiveRecordBase<E>> type, String through) {
		final String tableName = ReflectionUtils.getTableName(type);
		final String selection = tableName + "." + through + "=" + getId();
		return query(type, null, selection, null, null, null, null, null);
	}

	// ###  QUERY SHORTCUT METHODS

	/**
	 * Delete records in the table specified by the where clause.
	 * @param type the type of this object.
	 * @param id the primary key id of the record to be deleted.
	 * @return boolean returns true if the record was found and deleted.
	 */
	public static boolean delete(Class<? extends ActiveRecordBase<?>> type, long id) {
		return delete(type, "Id=?", new String[] { String.valueOf(id) }) > 0;
	}

	/**
	 * Load a single record by primary key.
	 * 
	 * @param type the type of this object.
	 * @param id the primary key id of the record to be loaded.
	 * @return <T> object returned by the query.
	 */
	public static <T> T load(Class<? extends ActiveRecordBase<?>> type, long id) {
		final String tableName = ReflectionUtils.getTableName(type);
		final String selection = tableName + ".Id=?";
		final String[] selectionArgs = new String[] { String.valueOf(id) };

		return querySingle(type, null, selection, selectionArgs, null, null, null);
	}

	/**
	 * Load the first record in a table.
	 * 
	 * @param type the type of this object.
	 * @return <T> object returned by the query.
	 */
	public static <T> T first(Class<? extends ActiveRecordBase<?>> type) {
		return querySingle(type, null, null, null, null, null, null);
	}

	/**
	 * Load the last record in a table.
	 * 
	 * @param type the type of this object
	 * @return <T> object returned by the query.
	 */
	public static <T> T last(Class<? extends ActiveRecordBase<?>> type) {
		return querySingle(type, null, null, null, null, null, "Id DESC");
	}

	// ### STANDARD METHODS

	/**
	 * Delete records in the table specified by the where clause.
	 * @param <T>
	 * @param type the type of this object
	 * @param whereClause the where clause.
	 * @param whereArgs arguments to be supplied to the where clause.
	 * @return int the number of records affected.
	 */
	public static int delete(Class<? extends ActiveRecordBase<?>> type, String whereClause, String[] whereArgs) {
		final SQLiteDatabase db = ApplicationCache.getInstance().openDatabase();
		final String table = ReflectionUtils.getTableName(type);

		final int count = db.delete(table, whereClause, whereArgs);

		return count;
	}

	/**
	 * Return an ArrayList of all records for the specified type, where, order by, group by, having, and limit clauses. Includes only the specified columns
	 * 
	 * @param type the type of this object.
	 * @param columns the columns to select, or null for all columns.
	 * @param selection selection clause applied to the query, or null for no clause.
	 * @param selectionArgs arguments to be supplied to the selection clause.
	 * @param groupBy group by clause applied to the query, or null for no clause.
	 * @param having having clause applied to the query, or null for no clause.
	 * @param orderBy order by clause applied to the query, or null for no clause.
	 * @param limit limit clause applied to the query (including distinct), or null for no clause.
	 * @return ArrayList<T> ArrayList of objects returned by the query.
	 */
	public static <T> ArrayList<T> query(Class<? extends ActiveRecordBase<?>> type, String[] columns, String selection,
			String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {

		final SQLiteDatabase db = ApplicationCache.getInstance().openDatabase();
		final Cursor cursor = db.query(ReflectionUtils.getTableName(type), columns, selection, selectionArgs, groupBy,
				having, orderBy, limit);

		final ArrayList<T> entities = processCursor(type, cursor);

		cursor.close();

		return entities;
	}

	/**
	 * Return a single object for the specified type, where, order by, group by, and having clauses. Includes only the specified columns
	 * 
	 * @param context the current context.
	 * @param type the type of this object.
	 * @param columns the columns to select, or null for all columns.
	 * @param selection selection clause applied to the query, or null for no clause.
	 * @param selectionArgs arguments to be supplied to the selection clause.
	 * @param groupBy group by clause applied to the query, or null for no clause.
	 * @param having having clause applied to the query, or null for no clause.
	 * @param orderBy order by clause applied to the query, or null for no clause.
	 * @return <T> object returned by the query.
	 */
	public static <T> T querySingle(Class<? extends ActiveRecordBase<?>> type, String[] columns, String selection,
			String[] selectionArgs, String groupBy, String having, String orderBy) {

		return (T) getFirst(query(type, columns, selection, selectionArgs, groupBy, having, orderBy, "1"));
	}

	// raw sql query

	/**
	 * Return an ArrayList of all records for the specified SQL query
	 * 
	 * @param context the current context.
	 * @param type the type of this object.
	 * @param sql the SQL query string.
	 * @return ArrayList<T> ArrayList of objects returned by the query.
	 */
	public static final <T> ArrayList<T> rawQuery(Class<? extends ActiveRecordBase<?>> type, String sql,
			String[] selectionArgs) {

		final SQLiteDatabase db = ApplicationCache.getInstance().openDatabase();
		final Cursor cursor = db.rawQuery(sql, selectionArgs);

		final ArrayList<T> entities = processCursor(type, cursor);

		cursor.close();

		return entities;
	}

	/**
	 * Return a single object for the specified SQL query
	 * 
	 * @param context the current context.
	 * @param type the type of this object.
	 * @param sql the SQL query string.
	 * @return <T> object returned by the query.
	 */
	public static final <T> T rawQuerySingle(Class<? extends ActiveRecordBase<?>> type, String sql,
			String[] selectionArgs) {

		return (T) getFirst(rawQuery(type, sql, selectionArgs));
	}

	////////////////////////////////////////////////////////////////////////////////
	// PRIVATE METHODS

	private static <T> T getFirst(ArrayList<T> entities) {
		if (entities.size() > 0) {
			return entities.get(0);
		}

		return null;
	}

	private static final <T> ArrayList<T> processCursor(Class<? extends ActiveRecordBase<?>> type, Cursor cursor) {
		final ArrayList<T> entities = new ArrayList<T>();

		try {
			Constructor<?> entityConstructor = type.getConstructor();

			if (cursor.moveToFirst()) {
				do {
					T entity = (T) entityConstructor.newInstance();
					((ActiveRecordBase<T>) entity).loadFromCursor(type, cursor);
					entities.add(entity);
				}
				while (cursor.moveToNext());
			}

		}
		catch (IllegalArgumentException e) {
			Log.e(Params.LOGGING_TAG, e.getMessage());
		}
		catch (InstantiationException e) {
			Log.e(Params.LOGGING_TAG, e.getMessage());
		}
		catch (IllegalAccessException e) {
			Log.e(Params.LOGGING_TAG, e.getMessage());
		}
		catch (InvocationTargetException e) {
			Log.e(Params.LOGGING_TAG, e.getMessage());
		}
		catch (SecurityException e) {
			Log.e(Params.LOGGING_TAG, e.getMessage());
		}
		catch (NoSuchMethodException e) {
			Log.e(Params.LOGGING_TAG, "Missing required constructor: " + e.getMessage());
		}

		return entities;
	}

	private final void loadFromCursor(Class<? extends ActiveRecordBase<?>> type, Cursor cursor) {
		final ArrayList<Field> fields = ReflectionUtils.getTableFields(type);

		for (Field field : fields) {
			final String fieldName = ReflectionUtils.getColumnName(field);
			Class<?> fieldType = field.getType();
			final int columnIndex = cursor.getColumnIndex(fieldName);

			if (columnIndex < 0) {
				continue;
			}

			field.setAccessible(true);

			try {
				boolean columnIsNull = cursor.isNull(columnIndex);
				TypeSerializer typeSerializer = mApplicationCache.getParserForType(fieldType);
				Object value = null;

				if (typeSerializer != null) {
					fieldType = TypeSerializer.TYPE_MAPPING.get(typeSerializer.getSerializedType());
				}

				if (columnIsNull) {
					field = null;
				}
				else if (fieldType.equals(String.class)) {
					value = cursor.getString(columnIndex);
				}
				else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
					value = cursor.getInt(columnIndex) != 0;
				}
				else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
					value = cursor.getInt(columnIndex);
				}
				else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
					value = cursor.getLong(columnIndex);
				}
				else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) {
					value = cursor.getFloat(columnIndex);
				}
				else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
					value = cursor.getDouble(columnIndex);
				}
				else if (fieldType.equals(Short.class) || fieldType.equals(short.class)) {
					value = cursor.getInt(columnIndex);
				}
				else if (fieldType.equals(Character.class) || fieldType.equals(char.class)) {
					value = cursor.getString(columnIndex).charAt(0);
				}
				else if (!fieldType.isPrimitive() && fieldType.getSuperclass() != null
						&& fieldType.getSuperclass().equals(ActiveRecordBase.class)) {

					long entityId = cursor.getLong(columnIndex);
					Class<? extends ActiveRecordBase<?>> entityType = (Class<? extends ActiveRecordBase<?>>) fieldType;

					ActiveRecordBase<?> entity = mApplicationCache.getEntity(entityType, entityId);

					if (entity == null) {
						entity = ActiveRecordBase.load(entityType, entityId);
					}

					value = entity;
				}

				// Use a deserializer if one is available
				if (typeSerializer != null && !columnIsNull) {
					value = typeSerializer.deserialize(value);
				}

				// Set the field value
				if (value != null) {
					field.set(this, value);
				}
			}
			catch (IllegalArgumentException e) {
				Log.e(Params.LOGGING_TAG, e.getMessage());
			}
			catch (IllegalAccessException e) {
				Log.e(Params.LOGGING_TAG, e.getMessage());
			}
			catch (SecurityException e) {
				Log.e(Params.LOGGING_TAG, e.getMessage());
			}
		}
	}

	////////////////////////////////////////////////////////////////////////////////
	// OVERRIDES

	@Override
	public boolean equals(Object obj) {
		final ActiveRecordBase<?> other = (ActiveRecordBase<?>) obj;

		return (this.mTableName == other.mTableName) && (this.mId == other.mId);
	}
}
