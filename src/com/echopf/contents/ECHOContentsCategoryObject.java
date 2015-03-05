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

package com.echopf.contents;

import com.echopf.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * {@.en An ECHOContentsCategoryObject is a particular contents category object.}
 * {@.ja カテゴリオブジェクト。{@link com.echopf.contents.ECHOContentsCategoriesMap}の構成要素。}
 */
public class ECHOContentsCategoryObject extends ECHODataObject 
										implements  Fetchable<ECHOContentsCategoryObject>, 
													Pushable<ECHOContentsCategoryObject>, 
													Deletable<ECHOContentsCategoryObject>,
													TreeNodeable<ECHOContentsCategoryObject> {

	private ECHOContentsCategoryObject newParent = null;
	
	
	/* Begin constructors */
	
	/**
	 * {@.en Constructs a new ECHOContentsCategoryObject.}
	 * {@.ja 新しいカテゴリをオブジェクトとして生成します。}
	 * 
	 * @param instanceId
	 * 		{@.en the reference ID of the instance to which this object belongs}
	 * 		{@.ja 新しいカテゴリを所属させるインスタンスのID}
	 */
	public ECHOContentsCategoryObject(String instanceId) {
		super(instanceId, "categories");
	}

	/**
	 * {@.en Constructs a new ECHOContentsCategoryObject based on an existing one on the ECHO server.}
	 * {@.ja 既存のカテゴリをオブジェクトとして生成します。}
	 * 
	 * @param instanceId
	 * 		{@.en the reference ID of the instance to which this object has belonged}
	 * 		{@.ja 既存カテゴリが所属するインスタンスのID}
	 * @param refid
	 * 		{@.en the reference ID of the existing one}
	 * 		{@.ja 既存カテゴリのID}
	 */
	public ECHOContentsCategoryObject(String instanceId, String refid) {
		super(instanceId, "categories", refid);
	}

	/**
	 * Constructs a new ECHOContentsCategoryObject based on an existing one on the ECHO server.
	 * @param instanceId the reference ID of the instance to which this object has belonged
	 * @param refid the reference ID of the existing one
	 * @param data : a copying category data object by JSONObject
	 */
	public ECHOContentsCategoryObject(String instanceId, String refid, JSONObject data) throws ECHOException {
		super(instanceId, "categories", refid, data);
	}

	/* End constructors */
	
	
	/*
	 * Implement Fetchable
	 * @see com.echopf.Fetchable#fetch()
	 */
	public ECHOContentsCategoryObject fetch() throws ECHOException {
		doFetch(true, null);
		return this;
	}
	
	
	/*
	 * Implement Fetchable
	 * @see com.echopf.Fetchable#fetchInBackground()
	 */
	public void fetchInBackground(FetchCallback<ECHOContentsCategoryObject> callback) {
		try {
			doFetch(false, callback);
		} catch (ECHOException e) {
			throw new InternalError();
		}
	}
	

	/*
	 * Implement Pushable
	 * @see com.echopf.Pushable#push()
	 */
	public ECHOContentsCategoryObject push() throws ECHOException {
		doPush(true, null);
		return this;
	}
	

	/*
	 * Implement Pushable
	 * @see com.echopf.Pushable#pushInBackground()
	 */
	public void pushInBackground(PushCallback<ECHOContentsCategoryObject> callback) {
		try {
			doPush(false, callback);
		} catch (ECHOException e) {
			throw new InternalError();
		}
	}

	
	/*
	 * Implement Deleteable
	 * @see com.echopf.Deleteable#delete()
	 */
	public ECHOContentsCategoryObject delete() throws ECHOException {
		doDelete(true, null);
		return this;
	}

	
	/*
	 * Implement Deleteable
	 * @see com.echopf.Deleteable#deleteInBackground()
	 */
	public void deleteInBackground(DeleteCallback<ECHOContentsCategoryObject> callback) {
		try {
			doDelete(false, callback);
		} catch (ECHOException e) {
			throw new InternalError();
		}
	}

	
	/*
	 * Implement TreeNodeable
	 * @see com.echopf.TreeNodeable#setNewParent()
	 */
	public void setNewParent(ECHOContentsCategoryObject newParent) {
		this.newParent = newParent;
	}

	
	/**
	 * Does Push data to the ECHO server in a background thread.
	 * @param sync : if set TRUE, then the main (UI) thread is waited for complete the pushing in a background thread. 
	 * 				 (a synchronous communication)
	 * @param callback invoked after the pushing is completed
	 * @throws ECHOException
	 */
	protected void doPush(boolean sync, PushCallback<ECHOContentsCategoryObject> callback) throws ECHOException {

		try {
			JSONObject apiObj  = new JSONObject(this.toString());
			
			if(this.newParent != null) {
				String new_parent_refid = this.newParent.getRefid();
				
				if(!new_parent_refid.isEmpty()) {
					apiObj.put("parent_refid", new_parent_refid);
					this.newParent = null;
				}
			}
			
			super.doPush(apiObj, sync, callback);
			
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	
	@Override
	protected void copyData(JSONObject data) throws ECHOException {
		JSONObject category = null;

		if(data == null) throw new ECHOException(0, "The copying data is not acceptable.");
		JSONArray categories = data.optJSONArray("categories");
		
		if(categories != null) { // if the data is a tree format
			category = categories.optJSONObject(0);
		}else{ // the data is a category object
			category = data;
		}
		
		if(category == null) throw new ECHOException(0, "The copying data is not acceptable.");
		
		category.remove("children");
		super.copyData(category);
	}
}