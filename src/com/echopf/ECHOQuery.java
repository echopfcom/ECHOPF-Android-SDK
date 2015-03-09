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

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.io.*;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;


/**
 * The ECHOQuery contains basic query methods used by the SDK.
 * @param <T>
 */
public class ECHOQuery {

	/**
	 * Disable constructor since this is an utility class.
	 */
    private ECHOQuery() {}


	/**
	 * Does Find objects from an object archive.
	 * @param sync : if set TRUE, then the main (UI) thread is waited for complete the finding in a background thread. 
	 * 				 (a synchronous communication)
	 * @param listKey the key associated with the object list
	 * @param clazz the object class
	 * @param callback invoked after the finding is completed
	 * @param instanceId the reference ID of the finding target instance
	 * @param resourceType the type of this object
	 * @param params to control the output objects
	 * @throws ECHOException
	 */
	@SuppressWarnings("rawtypes")
	public static <T extends ECHODataObject> List<T> doFind(final boolean sync, final String listKey, final String resourceType, final Class<T> clazz, 
			final FindCallback callback, final String instanceId, JSONObject params) throws ECHOException {
		
		// set default params
		if(params == null) params = new JSONObject();
		final boolean getall =  (params.optInt("limit") == 0);
		
		try {
			if(getall) params.put("limit", Integer.MAX_VALUE);
		} catch (JSONException e) {
			throw new InternalError();
		} 
		final JSONObject fParams = params;
		
		
		// Get ready a background thread
		final Handler handler = new Handler();
	    ExecutorService executor = Executors.newSingleThreadExecutor();
	    Callable<List<T>> communicator = new Callable<List<T>>() {
	    	  @Override
	    	  public List<T> call() throws ECHOException {
					List<T> objList = new ArrayList<T>();
					ECHOException exception = null;
					
					try {
						int currentPageNo = 0, currentPageCount;
						
						do {
							JSONObject response = getRequest(instanceId + "/" + resourceType , fParams);

							/* begin copying data */
							JSONArray items = response.optJSONArray(listKey);
							if(items == null) throw new ECHOException(0, "The copying data is not acceptable. That is why a records field is not specified.");
							
							for (int i = 0; i < items.length(); i++) {
								JSONObject item = items.optJSONObject(i);
								if(item == null) throw new ECHOException(0, "The copying data is not acceptable. That is why a members field is not specified.");
								
								String refid = item.optString("refid");
								if(refid.isEmpty()) throw new ECHOException(0, "The copying data is not acceptable. That is why a refid is not specified.");
	
								T obj = (T) clazz.getConstructor(String.class, String.class, JSONObject.class).newInstance(instanceId, refid, item);
								objList.add(obj);
							}
							/* end copying data */


							// begin pagination
							if(getall == false) break;
							
							JSONObject paginate = response.optJSONObject("paginate");
							if(paginate == null) throw new ECHOException(0, "The copying data is not acceptable.");
							
							if(currentPageNo+1 != paginate.optInt("page")) throw new ECHOException(0, "The copying data is not acceptable.");
							currentPageNo = paginate.optInt("page");
							if(currentPageNo == 0) throw new ECHOException(0, "The copying data is not acceptable.");

							currentPageCount = paginate.optInt("pageCount");
							if(currentPageCount == 0) throw new ECHOException(0, "The copying data is not acceptable.");
							
							if(currentPageNo >= currentPageCount) break;
							
							try {
								fParams.put("page", currentPageNo+1);
							} catch (JSONException e) {
								throw new InternalError();
							}

						} while(true);
					} catch (ECHOException e) {
						exception = e;
					} catch (InvocationTargetException e) {
						exception = (ECHOException) e.getCause();
					} catch (InstantiationException | IllegalAccessException | NoSuchMethodException | RuntimeException e) {
						exception = new ECHOException(e);
					}
					
					if(sync == false) {
						// Execute a callback method in the main (UI) thread.
						if(callback != null) {
							final ECHOException fException = exception;
							final List<T> fObjList = objList;
							
							handler.post(new Runnable() {
							    @SuppressWarnings("unchecked")
								@Override
								public void run() {
							    	callback.done(fObjList, fException);
								}
							});
						}
						
						return null;
					
					}else{
		    	  		
						if(exception == null) return objList;
						throw exception;
						
					}
	    	  }
	    };
        
	    Future< List<T> > future = executor.submit(communicator);
	    
	    if(sync) {
		    try {
		    	return future.get();
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
	    
	    return null;
	}
	
	
	/**
	 * Sends a GET request.
	 * @param path a request url path
	 * @throws ECHOException
	 */
	public static JSONObject getRequest(String path) throws ECHOException {
		return request(path, "GET");
	}

	
	/**
	 * Sends a GET request with optional request parameters.
	 * @param path a request url path
	 * @param query optional request parameters
	 * @throws ECHOException
	 */
	public static JSONObject getRequest(String path, JSONObject query) throws ECHOException {
		return request(path, "GET", query);
	}

	
	/**
	 * Sends a POST request.
	 * @param path a request url path
	 * @param data request contents
	 * @throws ECHOException
	 */
	public static JSONObject postRequest(String path, JSONObject data) throws ECHOException {
		return request(path, "POST", data);
	}

	
	/**
	 * Sends a PUT request.
	 * @param path a request url path
	 * @param data request contents
	 * @throws ECHOException
	 */
	public static JSONObject putRequest(String path, JSONObject data) throws ECHOException {
		return request(path, "PUT", data);
	}

	
	/**
	 * Sends a DELETE request
	 * @param path a request url path
	 * @throws ECHOException
	 */
	public static JSONObject deleteRequest(String path) throws ECHOException {
		return request(path, "DELETE");
	}


	/**
	 * Sends a HTTP request.
	 * @param path a request url path
	 * @param httpMethod a request method (GET/POST/PUT/DELETE)
	 * @throws ECHOException
	 */
	private static JSONObject request(String path, String httpMethod) throws ECHOException {
		return request(path, httpMethod, null);
	}
	

	/**
	 * Sends a HTTP request with optional request contents/parameters.
	 * @param path a request url path
	 * @param httpMethod a request method (GET/POST/PUT/DELETE)
	 * @param data request contents/parameters
	 * @throws ECHOException
	 */
	private static JSONObject request(String path, String httpMethod, JSONObject data) throws ECHOException  {
		final String secureDomain = ECHO.secureDomain;
		final String appId = ECHO.appId;
		final String appKey = ECHO.appKey;
		final String accessToken = ECHO.accessToken;
		
		if(secureDomain == null || appId == null || appKey == null) 
			throw new IllegalStateException("The SDK is not initialized.　Please call the ECHO.initialize().");
		
		
		String url = new StringBuilder("https://").append(secureDomain)
				.append("/").append(path).append("/rest_api=1.0/").toString();

		JSONObject responseObj = null;
		HttpsURLConnection http_client = null;

			
		// Build query_string
		if (httpMethod.equals("GET") && data != null) {
			boolean firstItem = true;
			Iterator<?> iter = data.keys();
			while (iter.hasNext()) {
				if (firstItem) {
					firstItem = false;
					url = url.concat("?");
				} else {
					url = url.concat("&");
				}
				String key = (String)iter.next();
				String value = data.optString(key);
				url = url.concat(key);
				url = url.concat("=");
				url = url.concat(value);
			}
		}

		
		try {

			URL url_conn = new URL(url.toString());
			http_client = (HttpsURLConnection) url_conn.openConnection();
			http_client.setRequestMethod(httpMethod);
			http_client.addRequestProperty("CONTENT-TYPE", "application/json");
			http_client.addRequestProperty("X-ECHO-APP-ID", appId);
			http_client.addRequestProperty("X-ECHO-APP-KEY", appKey);
			
			// Set access token
			if(accessToken != null && !accessToken.isEmpty()) {
				http_client.addRequestProperty("X-ECHO-ACCESS-TOKEN", accessToken);
			}

			// Build content
			if (!httpMethod.equals("GET") && data != null) {
				http_client.setDoOutput(true);
				http_client.setChunkedStreamingMode(0); // use default chunk size
				BufferedWriter wrBuffer = new BufferedWriter(new OutputStreamWriter(http_client.getOutputStream()));
				wrBuffer.write(data.toString());
				wrBuffer.close();
			}
			
			if (http_client.getResponseCode() != -1 /*== HttpURLConnection.HTTP_OK*/) {
				try {
					responseObj = ECHOQuery.getResponseObject(http_client.getInputStream());
				} catch (JSONException e) {
					throw new ECHOException(ECHOException.INVALID_JSON_FORMAT, "Invalid JSON format.");
				}
			}
			
		} catch (IOException e) {
			
			// get http response code
			int errorCode = -1;
			
			try {
				errorCode = http_client.getResponseCode();
			} catch (IOException e1) {
				throw new ECHOException(e1);
			}
			
			// get error contents
			try {
				responseObj = ECHOQuery.getResponseObject(http_client.getErrorStream());
			} catch (JSONException e1) {
				
				System.out.println(e1 + "::" + errorCode);
				
				if(errorCode == 404) {
					throw new ECHOException(ECHOException.RESOURCE_NOT_FOUND, "Resource not found.");
				}
				
				throw new ECHOException(ECHOException.INVALID_JSON_FORMAT, "Invalid JSON format.");
			}
			
			//
			if(responseObj != null) {
				int code = responseObj.optInt("error_code");
				String message = responseObj.optString("error_message");
				
				if(code != 0 || !message.equals("")) {
					JSONObject details = responseObj.optJSONObject("error_details");
					if(details == null) {
						throw new ECHOException(code, message);
					}else{
						throw new ECHOException(code, message, details);
					}
				}
			}
			
			throw new ECHOException(e);
			
		} finally {
			
			if (http_client != null) http_client.disconnect();
		
		}

		return responseObj;
	}

	
	/**
	 * Converts a response input stream into a JSON object.
	 */
	private static JSONObject getResponseObject(InputStream inputStream) throws JSONException {

		
		try {
			
			if(inputStream != null) {
				BufferedReader rdBuffer = new BufferedReader(new InputStreamReader(inputStream));
				StringBuilder json_string = new StringBuilder();
				String str = null;
				while ((str = rdBuffer.readLine()) != null) {
					json_string.append(str);
				}
				rdBuffer.close();
				
				return new JSONObject(json_string.toString());	
			}
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return null;
	}
}