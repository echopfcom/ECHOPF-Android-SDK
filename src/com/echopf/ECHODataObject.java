/*******
 Copyright 2015 NeuroBASE,Inc. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 
 http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 **********/

package com.echopf;

import java.text.ParseException;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.*;

import com.echopf.contents.blogs.ECHOEntryObject;
import com.echopf.contents.databases.ECHORecordObject;
import com.echopf.members.ECHOMemberObject;

import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;


/**
 * An ECHODataObject is an abstract data object.
 * Particular data objects are implemented based on this class.
 */
public abstract class ECHODataObject<S extends ECHODataObject<S>> extends ECHOObject implements Parcelable {
	
	protected JSONObject data = null;
	private ECHOACLObject newACL = null;
	private ECHOACLObject currentACL = null;
	private Boolean multipart = null;
	
	
	/* Begin constructors */

	/**
	 * Constructs a new ECHODataObject.
	 * 
	 * @param instanceId the reference ID of the instance to which this object belongs
	 * @param resourceType the type of this object
	 */
	protected ECHODataObject(String instanceId, String resourceType) {
		this(instanceId, resourceType, null);
	}
	
	
	/**
	 * Constructs a new ECHODataObject based on an existing one on the remote server.
	 * 
	 * @param instanceId the reference ID of the instance to which the object has belonged
	 * @param resourceType the type of this object
	 * @param refid the reference ID of the existing one
	 */
	protected ECHODataObject(String instanceId, String resourceType, String refid) {
		super(instanceId, resourceType, refid);
		this.data = new JSONObject();
		
		try {
			this.data.put("refid", refid);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/* End constructors */
	

	/**
	 * Factory method of particular data objects.
	 * 
	 * @param data a source JSONObject
	 */
	public static ECHODataObject<?> factory(JSONObject data) {

		try {
			
			String refid = data.getString("refid");
			String resourceType = data.getString("resource_type");

			String urlPath = data.getString("url_path");
			Matcher m = Pattern.compile("^/([^/]+)").matcher(urlPath);
			
			if(!m.find()) return null;
			String instanceId = m.group(1);
			
			
			if(resourceType.equals("entry")) { // ECHOEntryObject
				return new ECHOEntryObject(instanceId, refid, data);
			}else if(resourceType.equals("record")) { // ECHODatabaseObject
				return new ECHORecordObject(instanceId, refid, data);
			}else if(resourceType.equals("member")) { // ECHOMemberObject
				return new ECHOMemberObject(instanceId, refid, data);	
			}
			
		} catch (Exception ignored) {
			// skip
		}
		
		return null;
	}
	

	/**
	 * Does Fetch data from the remote server in a background thread.
	 * 
	 * @param sync if set TRUE, then the main (UI) thread is waited for complete the fetching in a background thread. 
	 * 				 (a synchronous communication)
	 * @param callback invoked after the fetching is completed
	 * @throws ECHOException 
	 */
	protected void doFetch(final boolean sync, final FetchCallback<S> callback) throws ECHOException {
		final Handler handler = new Handler();
		
		// Get ready a background thread
	    ExecutorService executor = Executors.newSingleThreadExecutor();
	    Callable<Object> communictor = new Callable<Object>() {

	    	@Override
	    	public Object call() throws ECHOException {

    			ECHOException exception = null;
    			JSONObject data = null;

    			try {

    	    		synchronized (lock) {
	    				data = ECHOQuery.getRequest(getRequestURLPath());
	    				copyData(data);
    	    		}
    	    		
    			} catch(ECHOException e) {
    				exception = e;
				} catch (Exception e) {
					exception = new ECHOException(e);
				}

    			
    			if(sync == false) {
    				
					// Execute a callback method in the main (UI) thread.
					if(callback != null) {
						final ECHOException fException = exception;
						
						handler.post(new Runnable() {
							@Override @SuppressWarnings("unchecked")
							public void run() {
								callback.done((S) ECHODataObject.this, fException);
							}
						});
					}
    			
    			}else{
    				
    				if(exception != null) throw exception;
    			
    			}
				
	    		return null;
	    	}
	    };
	    
	    Future<Object> future = executor.submit(communictor);

	    if(sync) {
		    try {
		    	future.get();
		    } catch (InterruptedException e) {	
		    	Thread.currentThread().interrupt(); // ignore/reset
		    } catch (ExecutionException e) {
		    	Throwable e2 = e.getCause();
		    	
		    	if (e2 instanceof ECHOException) {
		    		throw (ECHOException) e2;
		    	}
		    	
		    	throw new RuntimeException(e2);
		    }
	    }
	}
	

	/**
	 * Does Push data to the remote server in a background thread.
	 * 
	 * @param sync if set TRUE, then the main (UI) thread is waited for complete the pushing in a background thread. 
	 * 				 (a synchronous communication)
	 * @param callback invoked after the pushing is completed
	 * @throws ECHOException 
	 */
	protected void doPush(final boolean sync, final PushCallback<S> callback) throws ECHOException {
		
		final JSONObject obj = buildRequestContents();
		
		if(this.multipart == null) throw new IllegalStateException("`buildRequestContents()` had not been completed.");
		final boolean fMultipart = this.multipart;
		
		// Get ready a background thread
		final Handler handler = new Handler();
	    ExecutorService executor = Executors.newSingleThreadExecutor();
	    Callable<Object> communictor = new Callable<Object>() {

	    	@Override
	    	public Object call() throws ECHOException {

				JSONObject data = null;
				ECHOException exception = null;
    			
				try {
		    		synchronized (lock) {

		    			if(refid == null) { // post
		    				if(fMultipart == false) {
		    					data = ECHOQuery.postRequest(getRequestURLPath(), obj);
		    				}else{
		    					data = ECHOQuery.multipartPostRequest(getRequestURLPath(), obj);
		    				}
						}else{ // put
		    				if(fMultipart == false) {
		    					data = ECHOQuery.putRequest(getRequestURLPath(), obj);
		    				}else{
		    					data = ECHOQuery.multipartPutRequest(getRequestURLPath(), obj);
		    				}
						}
						
						refid = data.optString("refid");
						copyData(data);
		    		}
		    		
				} catch (ECHOException e) {
					exception = e;
				} catch (Exception e) {
					exception = new ECHOException(e);
				}
				
				
				if(sync == false) {
	
					// Execute a callback method in the main (UI) thread.
					if(callback != null) {
						final ECHOException fException = exception;
						
						handler.post(new Runnable() {
							@Override @SuppressWarnings("unchecked")
							public void run() {
								callback.done((S) ECHODataObject.this, fException);
							}
						});
					}
					
				}else{
	    	  		
					if(exception != null) throw exception;
					
				}

	    		return null;
	    	}
	    };
	    
	    Future<Object> future = executor.submit(communictor);

	    if(sync) {
		    try {
		    	future.get();
		    } catch (InterruptedException e) {	
		    	Thread.currentThread().interrupt(); // ignore/reset
		    } catch (ExecutionException e) {
		    	Throwable e2 = e.getCause();
		    	
		    	if (e2 instanceof ECHOException) {
		    		throw (ECHOException) e2;
		    	}
		    	
		    	throw new RuntimeException(e2);
		    }
	    }
	}
	


	/**
	 * Does Delete an object from the remote server in a background thread.
	 * 
	 * @param sync if set TRUE, then the main (UI) thread is waited for complete the deleting in a background thread. 
	 * 				 (a synchronous communication)
	 * @param callback invoked after the deleting is completed
	 * @throws ECHOException 
	 */
	protected void doDelete(final boolean sync, final DeleteCallback<S> callback) throws ECHOException {
		final Handler handler = new Handler();

		// Get ready a background thread
	    ExecutorService executor = Executors.newSingleThreadExecutor();
	    Callable<Object> communictor = new Callable<Object>() {

	    	@Override
	    	public Object call() throws ECHOException {
		
				JSONObject data = null;
				ECHOException exception = null;
				try {
					
		    		synchronized (lock) {	
						data = ECHOQuery.deleteRequest(getRequestURLPath());
						
						refid = null;
						copyData(data);
		    		}
					
				} catch(ECHOException e) {
					exception = e;
				} catch (Exception e) {
					exception = new ECHOException(e);
				}

				
				if(sync == false) {
					
					// Execute a callback method in the main (UI) thread.
					if(callback != null) {
						final ECHOException fException = exception;
						
						handler.post(new Runnable() {
							@Override @SuppressWarnings("unchecked")
							public void run() {
								callback.done((S) ECHODataObject.this, fException);
							}
						});
					}
				
				}else{
    	  		
					if(exception != null) throw exception;
				
				}
				
	    		return null;
	    	}
	    };
	    
	    Future<Object> future = executor.submit(communictor);

	    if(sync) {
		    try {
		    	future.get();
		    } catch (InterruptedException e) {	
		    	Thread.currentThread().interrupt(); // ignore/reset
		    } catch (ExecutionException e) {
		    	Throwable e2 = e.getCause();
		    	
		    	if (e2 instanceof ECHOException) {
		    		throw (ECHOException) e2;
		    	}
		    	
		    	throw new RuntimeException(e2);
		    }
	    }
	}

	
	/**
	 * {@.en Gets the current ACLObject of this object.}
	 * {@.ja このオブジェクトに設定されているACLを取得する。}
	 */
	public ECHOACLObject getACL() {
		return this.currentACL;
	}
	

	/**
	 * {@.en Sets the new ACLObject.}
	 * {@.ja 新しいACLをセットする。サーバーへ反映させるには、保存処理を行ってください。}
	 */
	public void setNewACL(ECHOACLObject newACL) {
		this.newACL = newACL;
	}
	

	/**
	 * Build a request contents object.
	 */
	protected JSONObject buildRequestContents() {
		JSONObject obj = new JSONObject();

		this.multipart = false; // initialize multipart

		try {

			// acl
			if(newACL != null) {
				System.out.println(newACL.toJSONObject());
				obj.put("acl", newACL.toJSONObject());
				newACL = null;
			}

			Iterator<?> iter = this.data.keys();
			while (iter.hasNext()) {
				String key = (String)iter.next();

				// readonly fields
				if (key.equals("created") || key.equals("modified")) {

					continue; // skip

				// contents
				} else if (key.equals("contents")) {

					JSONObject contentsObj = this.data.optJSONObject("contents");
					if(contentsObj == null) continue; // skip

					Iterator<?> iter2 = contentsObj.keys();
					while (iter2.hasNext()) {
						String key2 = (String) iter2.next();

						Object elemObj = contentsObj.opt(key2);

						if(elemObj instanceof JSONArray) { // array field
							JSONArray array = (JSONArray)elemObj;

							for (int key3=0; key3<array.length(); key3++) {
								Object elemArrayObj = array.opt(key3);
								array.put(key3, convertContentsInBuildRequest(elemArrayObj));
							}

						}else{ // not array field

							contentsObj.put(key2, convertContentsInBuildRequest(elemObj));

						}
					}

					obj.put("contents", contentsObj);


				// others
				} else {

					obj.put(key, this.data.opt(key));

				}

			}

		} catch (JSONException e) {
			throw new RuntimeException(e);
		}


		return obj;
	}
	
	private Object convertContentsInBuildRequest(Object elemObj) {
	
		if(elemObj instanceof ECHOFile) { // _type = file
			
			// if a local file is set, request by multipart/form-data
			if (((ECHOFile)elemObj).getLocalBytes() != null) {
				this.multipart = true;
			} else {
				return JSONObject.NULL;
			}

		}else if(elemObj instanceof ECHODataObject) { // _type = instance
		
			String refid = ((ECHODataObject<?>) elemObj).refid;
			return (refid.isEmpty()) ? JSONObject.NULL : refid;
			
		}else if(elemObj instanceof ECHODate) { // _type = date
			
			return ((ECHODate)elemObj).toStringForECHO();
			
		}

		return elemObj;
	}
	

	/**
	 * Copy data from a JSONObject
	 * 
	 * @param source the source JSONObject
	 */
	protected void copyData(JSONObject source) {
		if(source == null) throw new IllegalArgumentException("Argument `source` must not be null.");

		// Reset current all data
		if(this.data.length() > 0) this.data = new JSONObject();

		// Copying input data
		Iterator<?> iter = source.keys();
		while (iter.hasNext()) {
			String key = (String)iter.next();

			try {
				
				// date
				if (key.equals("created") || key.equals("modified")) {
					
					String date = source.optString(key);
					if(date == null) continue; // skip

					try {
						this.data.put(key, new ECHODate(date));
					} catch (ParseException ignored) {
						// skip
					}

				// acl
				} else if (key.equals("acl")) {

					JSONObject aclObj = source.optJSONObject("acl");
					if(aclObj != null) this.currentACL = new ECHOACLObject(aclObj);

				// contents
				} else if(key.equals("contents")) {
	
					JSONObject contentsObj = source.optJSONObject("contents");
					if(contentsObj == null) continue; // skip
					
					Iterator<?> iter2 = contentsObj.keys();
					while (iter2.hasNext()) {
						String key2 = (String) iter2.next();						
						Object elemObj = contentsObj.opt(key2);
						if(elemObj == null) continue;
						
						if(elemObj instanceof JSONArray) { // array field
							JSONArray array = (JSONArray)elemObj;
							
							for (int key3=0; key3<array.length(); key3++) {
								Object elemArrayObj = array.opt(key3);
								array.put(key3, convertContentsInCopyData(elemArrayObj));
							}
							
						}else{ // not array field
							
							contentsObj.put(key2, convertContentsInCopyData(elemObj));
							
						}
					}

					this.data.put(key, contentsObj);

					// others
				} else {

					this.data.put(key, source.opt(key));

				}

			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			
		}
	}

	private Object convertContentsInCopyData(Object source) {
		
		if(source instanceof JSONObject) {
		
			JSONObject elemJSONObj = (JSONObject)source;
			String type = elemJSONObj.optString("_type");
			
			if(type.equals("file")) { // _type = file
				return new ECHOFile(elemJSONObj);
			} else if (type.equals("instance")) { // _type = instance
				return ECHODataObject.factory(elemJSONObj);
			}
		
		} else if (source instanceof String && ((String) source).length() == 19) { // (maybe) _type = date
			
			try {
				return new ECHODate((String) source);
			} catch (ParseException ignored) {
				// Wrong date string
			}

		}
		
		return source;
	}
	
	

	/* Begin JSONObject operators */
	
	/**
	 * Appends value to the array already mapped to name. If this object has no mapping for name, this inserts a new mapping. If the mapping exists but its value is not an array, the existing and new values are inserted in order into a new array which is itself mapped to name. In aggregate, this allows values to be added to a mapping one at a time.
	 *
	 * Note that append(String, Object) provides better semantics. In particular, the mapping for name will always be a JSONArray. Using accumulate will result in either a JSONArray or a mapping whose type is the type of value depending on the number of calls to it.
	 * @params value : a JSONObject, JSONArray, String, Boolean, Integer, Long, Double, NULL or null. May not be NaNs or infinities.
	 * @throws ECHOException
	 */
	@SuppressWarnings("unchecked")
	public S accumulate(String name, Object value) throws ECHOException {
		try {
			data.accumulate(name, value);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
		
		return (S) this;
	}
	
	/**
	 * Returns the value mapped by name, or throws if no such mapping exists.
	 * @throws ECHOException
	 */
	public Object get(String name) throws ECHOException {
		try {
			return data.get(name);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
	}
	
	/**
	 * Returns the value mapped by name if it exists and is a boolean or can be coerced to a boolean, or throws otherwise.
	 * @throws ECHOException
	 */
	public boolean getBoolean(String name) throws ECHOException {
		try {
			return data.getBoolean(name);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
	}
	
	/**
	 * Returns the value mapped by name if it exists and is a double or can be coerced to a double, or throws otherwise.
	 * @throws ECHOException
	 */
	public double getDouble(String name) throws ECHOException {
		try {
			return data.getDouble(name);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
	}
	
	/**
	 * Returns the value mapped by name if it exists and is an int or can be coerced to an int, or throws otherwise.
	 * @throws ECHOException
	 */
	public int getInt(String name) throws ECHOException {
		try {
			return data.getInt(name);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is a JSONArray, or throws otherwise.
	 * @throws ECHOException
	 */
	public JSONArray getJSONArray(String name) throws ECHOException {
		try {
			return data.getJSONArray(name);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is a JSONObject, or throws otherwise.
	 * @throws ECHOException
	 */
	public JSONObject getJSONObject(String name) throws ECHOException {
		try {
			return data.getJSONObject(name);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is a long or can be coerced to a long, or throws otherwise. Note that JSON represents numbers as doubles, so this is lossy; use strings to transfer numbers via JSON.
	 * @throws ECHOException
	 */
	public long getLong(String name) throws ECHOException {
		try {
			return data.getLong(name);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
	} 
	
	/**
	 * Returns the value mapped by name if it exists, coercing it if necessary, or throws if no such mapping exists.
	 * @throws ECHOException
	 */
	public String getString(String name) throws ECHOException {
		try {
			return data.getString(name);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
	} 
	
	/**
	 * Returns true if this object has a mapping for name. The mapping may be NULL.
	 */
	public boolean has(String name) {
		return data.has(name);
	} 
	
	/**
	 * Returns true if this object has no mapping for name or if it has a mapping whose value is NULL.
	 */
	public boolean isNull(String name) {
		return data.isNull(name);
	} 
	
	/**
	 * Returns an iterator of the String names in this object. The returned iterator supports remove, which will remove the corresponding mapping from this object. If this object is modified after the iterator is returned, the iterator's behavior is undefined. The order of the keys is undefined.
	 */
	public Iterator<String> keys() {
		return data.keys();
	} 
	
	/**
	 * Returns the number of name/value mappings in this object.
	 * @throws ECHOException
	 */
	public int length() {
		return data.length();
	} 
	
	/**
	 * Returns an array containing the string names in this object. This method returns null if this object contains no mappings.
	 */
	public JSONArray names() {
		return data.names();
	} 
	
	/**
	 * Returns the value mapped by name, or null if no such mapping exists.
	 */
	public Object opt(String name) {
		return data.opt(name);
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is a boolean or can be coerced to a boolean, or false otherwise.
	 */
	public boolean optBoolean(String name) {
		return data.optBoolean(name);
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is a boolean or can be coerced to a boolean, or fallback otherwise.
	 */
	public boolean optBoolean(String name, boolean fallback) {
		return data.optBoolean(name, fallback);
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is a double or can be coerced to a double, or fallback otherwise.
	 */
	public double optDouble(String name, double fallback) {
		return data.optDouble(name, fallback);
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is a double or can be coerced to a double, or NaN otherwise.
	 */
	public double optDouble(String name) {
		return data.optDouble(name);
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is an int or can be coerced to an int, or fallback otherwise.
	 */
	public int optInt(String name, int fallback) {
		return data.optInt(name, fallback);
	}  
	
	/**
	 * Returns the value mapped by name if it exists and is an int or can be coerced to an int, or 0 otherwise.
	 */
	public int optInt(String name) {
		return data.optInt(name);
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is a JSONArray, or null otherwise.
	 */
	public JSONArray optJSONArray(String name) {
		return data.optJSONArray(name);
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is a JSONObject, or null otherwise.
	 */
	public JSONObject optJSONObject(String name) {
		return data.optJSONObject(name);
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is a long or can be coerced to a long, or 0 otherwise. Note that JSON represents numbers as doubles, so this is lossy; use strings to transfer numbers via JSON.
	 */
	public long optLong(String name) {
		return data.optLong(name);
	} 
	
	/**
	 * Returns the value mapped by name if it exists and is a long or can be coerced to a long, or fallback otherwise. Note that JSON represents numbers as doubles, so this is lossy; use strings to transfer numbers via JSON.
	 */
	public long optLong(String name, long fallback) {
		return data.optLong(name, fallback);
	} 
	
	/**
	 * Returns the value mapped by name if it exists, coercing it if necessary, or the empty string if no such mapping exists.
	 */
	public String optString(String name) {
		return data.optString(name);
	} 
	
	/**
	 * Returns the value mapped by name if it exists, coercing it if necessary, or fallback if no such mapping exists.
	 */
	public String optString(String name, String fallback) {
		return data.optString(name, fallback);
	} 
	
	/**
	 * Maps name to value, clobbering any existing name/value mapping with the same name.
	 * @throws ECHOException 
	 */
	@SuppressWarnings("unchecked")
	public S put(String name, int value) throws ECHOException {
		try {
			data.put(name, value);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
		
		return (S) this;
	} 
	
	/**
	 * Maps name to value, clobbering any existing name/value mapping with the same name.
	 * @throws ECHOException 
	 */
	@SuppressWarnings("unchecked")
	public S put(String name, long value) throws ECHOException {
		try {
			data.put(name, value);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
		
		return (S) this;
	} 
	
	/**
	 * Maps name to value, clobbering any existing name/value mapping with the same name. If the value is null, any existing mapping for name is removed.
	 * @throws ECHOException 
	 */
	@SuppressWarnings("unchecked")
	public S put(String name, Object value) throws ECHOException {
		try {
			data.put(name, value);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
		
		return (S) this;
	} 
	
	/**
	 * Maps name to value, clobbering any existing name/value mapping with the same name.
	 * @throws ECHOException 
	 */
	@SuppressWarnings("unchecked")
	public S put(String name, boolean value) throws ECHOException {
		try {
			data.put(name, value);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
		
		return (S) this;
	} 
	
	/**
	 * Maps name to value, clobbering any existing name/value mapping with the same name.
	 * @throws ECHOException 
	 */
	@SuppressWarnings("unchecked")
	public S put(String name, double value) throws ECHOException {
		try {
			data.put(name, value);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
		
		return (S) this;
	} 
	
	/**
	 * Equivalent to put(name, value) when both parameters are non-null; does nothing otherwise.
	 * @throws ECHOException 
	 */
	@SuppressWarnings("unchecked")
	public S putOpt(String name, Object value) throws ECHOException {
		try {
			data.putOpt(name, value);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
		
		return (S) this;
	} 
	
	/**
	 * Removes the named mapping if it exists; does nothing otherwise.
	 * @return the value previously mapped by name, or null if there was no such mapping.
	 */
	public Object remove(String name) {
		return data.remove(name);
	} 
	
	/**
	 * Equivalent to put(name, value) when both parameters are non-null; does nothing otherwise.
	 * @throws ECHOException 
	 */
	public JSONArray toJSONArray(JSONArray name) throws ECHOException {
		try {
			return data.toJSONArray(name);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
	} 

	/**
	 * Encodes this object as a compact JSON string, such as:
	 * <pre>
	 * {"query":"Pizza","locations":[94043,90210]}
	 * </pre>
	 * @throws ECHOException 
	 */
	public String toString() {
		return data.toString();
	} 

	/**
	 * Encodes this object as a human readable JSON string for debugging, such as:
	 * <pre>
	 *	 {
	 *	     "query": "Pizza",
	 *	     "locations": [
	 *	         94043,
	 *	         90210
	 *	     ]
	 *	 }
	 * </pre>
	 * @param indentSpaces	the number of spaces to indent for each level of nesting.
	 * @throws ECHOException 
	 */
	public String toString(int indentSpaces) throws ECHOException {
		try {
			return data.toString(indentSpaces);
		} catch (JSONException e) {
			throw new ECHOException(e);
		}
	} 
	
	/* End JSONObject operators */

	
	/* Begin Parcel methods */
	
    public int describeContents() {  
        return 0;  
    }

    public void writeToParcel(Parcel out, int flags) {
    	out.writeString(this.instanceId);
    	out.writeString(this.resourceType);
    	out.writeString(this.refid);

    	out.writeString((data != null) ? data.toString() : "");
        out.writeString((newACL != null) ? newACL.toString() : "");
        out.writeString((currentACL != null) ? currentACL.toString() : "");
        out.writeString((multipart != null) ? multipart.toString() : "");
    }
    
    @SuppressLint("UseValueOf")
	protected ECHODataObject(Parcel in) {
    	this(in.readString(), in.readString(), in.readString());
    	
    	try {
    		String strData = in.readString();
			if(!strData.isEmpty()) copyData(new JSONObject(strData));
			
			String strNewACL = in.readString();
			if(!strNewACL.isEmpty()) newACL = new ECHOACLObject(new JSONObject());
			
			String strCurrentACL = in.readString();
			if(!strCurrentACL.isEmpty()) currentACL = new ECHOACLObject(new JSONObject(strCurrentACL));
		
    	} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    	
    	String strMultipart = in.readString();
    	if (!strMultipart.isEmpty()) multipart = new Boolean(strMultipart);
    }
    
 	/* End Parcel methods */
}
