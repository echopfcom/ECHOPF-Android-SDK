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

package com.echopf.members;

import android.os.Parcel;
import android.os.Parcelable;

import com.echopf.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * {@.en An ECHOMembersGroupObject is a particular group data.}
 * {@.ja グループオブジェクト。{@link com.echopf.members.ECHOMembersGroupsMap}の構成要素。}
 */
public class ECHOMembersGroupObject extends ECHODataObject<ECHOMembersGroupObject>
									implements  Fetchable<ECHOMembersGroupObject>, 
												Pushable<ECHOMembersGroupObject>, 
												Deletable<ECHOMembersGroupObject>,
												TreeNodeable<ECHOMembersGroupObject> {

	private ECHOMembersGroupObject newParent = null;
	
	
	/* Begin constructors */

	/**
	 * {@.en Constructs a new ECHOMembersGroupObject.}
	 * {@.ja 新しいグループをオブジェクトとして生成します。}
	 * 
	 * @param instanceId 
	 * 		{@.en the reference ID of the instance to which this object belongs}
	 * 		{@.ja 新しいグループを所属させるメンバーインスタンスのID}
	 */
	public ECHOMembersGroupObject(String instanceId) {
		super(instanceId, "groups");
	}
	
	/**
	 * {@.en Constructs a new ECHOMembersGroupObject based on an existing one on the ECHO server.}
	 * {@.ja 既存のグループをオブジェクトとして生成します。}
	 * 
	 * @param instanceId
	 * 		{@.en the reference ID of the instance to which this object has belonged}
	 * 		{@.ja 既存グループが所属するメンバーインスタンスのID}
	 * @param refid
	 * 		{@.en the reference ID of the existing one}
	 * 		{@.ja 既存グループのID}
	 */
	public ECHOMembersGroupObject(String instanceId, String refid) {
		super(instanceId, "groups", refid);
	}

	/**
	 * Constructs a new ECHOMembersGroupObject based on an existing one on the ECHO server.
	 * @param instanceId the reference ID of the instance to which this object has belonged
	 * @param refid the reference ID of the existing one
	 * @param source a copying group object in JSONObject
	 */
	public ECHOMembersGroupObject(String instanceId, String refid, JSONObject source) {
		this(instanceId, refid);
		copyData(source);
	}

	/* End constructors */
	
	
	/*
	 * Implement a Fetchable
	 * @see com.echopf.Fetchable#fetch()
	 */
	public ECHOMembersGroupObject fetch() throws ECHOException {
		doFetch(true, null);
		return this;
	}
	
	
	/*
	 * Implement a Fetchable
	 * @see com.echopf.Fetchable#fetchInBackground()
	 */
	public void fetchInBackground(FetchCallback<ECHOMembersGroupObject> callback) {
		try {
			doFetch(false, callback);
		} catch (ECHOException e) {
			throw new InternalError();
		}
	}
	

	/*
	 * Implement a Pushable
	 * @see com.echopf.Pushable#push()
	 */
	public ECHOMembersGroupObject push() throws ECHOException {
		doPush(true, null);
		return this;
	}
	

	/*
	 * Implement a Pushable
	 * @see com.echopf.Pushable#pushInBackground()
	 */
	public void pushInBackground(PushCallback<ECHOMembersGroupObject> callback) {
		try {
			doPush(false, callback);
		} catch (ECHOException e) {
			throw new InternalError();
		}
	}

	
	/*
	 * Implement a Deleteable
	 * @see com.echopf.Deleteable#delete()
	 */
	public ECHOMembersGroupObject delete() throws ECHOException {
		doDelete(true, null);
		return this;
	}

	
	/*
	 * Implement a Deleteable
	 * @see com.echopf.Deleteable#deleteInBackground()
	 */
	public void deleteInBackground(DeleteCallback<ECHOMembersGroupObject> callback) {
		try {
			doDelete(false, callback);
		} catch (ECHOException e) {
			throw new InternalError();
		}
	}

	
	/*
	 * Implement a TreeNodeable
	 * @see com.echopf.TreeNodeable#setNewParent()
	 */
	public void setNewParent(ECHOMembersGroupObject newParent) {
		this.newParent = newParent;
	}

	
	@Override
	protected JSONObject buildRequestContents()  {
		JSONObject obj = super.buildRequestContents();
		

		try {

			// parent_refid
			obj.remove("parent_refid");
			if(this.newParent != null) {
				String new_parent_refid = this.newParent.getRefid();
				
				if(!new_parent_refid.isEmpty()) {
					obj.put("parent_refid", new_parent_refid);
					this.newParent = null;
				}
			}
			
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}

		return obj;
	}

	
	@Override
	protected void copyData(JSONObject source) {
		if(data == null) throw new IllegalArgumentException("Argument `source` must not be null.");

		JSONArray groups = source.optJSONArray("groups");

		JSONObject group = null;
		if(groups != null) { // if the data is a tree format
			group = groups.optJSONObject(0);
		}else{ // the data is a group object
			group = source;
		}
		
		if(group == null) return; // skip

		group.remove("children"); // remove children

		// super
		super.copyData(group);
	}


	/* Begin Parcel methods */
	
	public static final Parcelable.Creator<ECHOMembersGroupObject> CREATOR = new Parcelable.Creator<ECHOMembersGroupObject>() {  
        public ECHOMembersGroupObject createFromParcel(Parcel in) {  
            return new ECHOMembersGroupObject(in);
        }
        
        public ECHOMembersGroupObject[] newArray(int size) {  
            return new ECHOMembersGroupObject[size];  
        }  
    };
	
    public ECHOMembersGroupObject(Parcel in) {
    	super(in);
     }
     
 	/* End Parcel methods */
}
