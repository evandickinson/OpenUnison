/*
Copyright 2015 Tremolo Security, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/


package com.tremolosecurity.provisioning.core.providers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.commons.dbcp.cpdsadapter.DriverAdapterCPDS;
import org.apache.commons.dbcp.datasources.SharedPoolDataSource;
import org.apache.logging.log4j.Logger;

import com.novell.ldap.LDAPException;
import com.tremolosecurity.config.util.ConfigManager;
import com.tremolosecurity.provisioning.core.ProvisioningUtil.ActionType;
import com.tremolosecurity.provisioning.core.ProvisioningException;
import com.tremolosecurity.provisioning.core.User;
import com.tremolosecurity.provisioning.core.UserStoreProvider;
import com.tremolosecurity.provisioning.core.Workflow;
import com.tremolosecurity.provisioning.core.providers.db.CustomDB;
import com.tremolosecurity.saml.Attribute;



public class BasicDB implements BasicDBInterface {

	static Logger logger = org.apache.logging.log4j.LogManager.getLogger(BasicDB.class.getName());
	
	String driver;
	String url;
	String user;
	String pwd;
	
	String name;
	
	int maxCons;
	int maxIdleCons;
	
	DataSource ds;
	
	String groupTable;
	String userTable;
	String userPrimaryKey;
	String groupUserKey;
	String groupGroupKey;
	String groupLinkTable;
	String groupName;
	String groupPrimaryKey;
	String userName;
	
	String beginEscape;
	String endEscape;
	
	String validationQuery;
	
	CustomDB customDBProvider;
	
	enum GroupManagementMode {
		None,
		Many2Many,
		One2Many, Custom
	};
	
	GroupManagementMode groupMode;

	private String userSQL;

	private String groupSQL;

	private ConfigManager cfgMgr;
	
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.provisioning.core.providers.BasicDB#createUser(com.tremolosecurity.provisioning.core.User, java.util.Set, java.util.Map)
	 */
	
	@Override
	public void createUser(User user, Set<String> attributes,Map<String,Object> request)
			throws ProvisioningException {

		int userID = 0;
		int approvalID = 0;
		int workflow = 0;
		
		if (request.containsKey("TREMOLO_USER_ID")) {
			userID = (Integer) request.get("TREMOLO_USER_ID");
		}
		
		if (request.containsKey("APPROVAL_ID")) {
			approvalID = (Integer) request.get("APPROVAL_ID");
		}
		
		if (request.containsKey("WORKFLOW_ID")) {
			workflow = (Integer) request.get("WORKFLOW_ID");
		}
		
		Map<String,Attribute> attrs = new HashMap<String,Attribute>();
		attrs.putAll(user.getAttribs());
		
		if (! attrs.containsKey(this.userName)) {
			attrs.remove("userid");
			attrs.put(this.userName, new Attribute(this.userName,user.getUserID()));
		}
		
		
		Connection con = null;
		try {
		
			
			
			
			con = this.ds.getConnection();
			con.setAutoCommit(false);
			
			
			int userid = -1;
			
			if (this.customDBProvider != null) {
				Map<String,Attribute> toadd = new HashMap<String,Attribute>();
				
				for (String attr : attributes) {
					if (attrs.get(attr) != null) {
						toadd.put(attr,user.getAttribs().get(attr));
					}
				}
				
				userid = this.customDBProvider.createUser(con,user, toadd);
				for (String groupName : user.getGroups()) {
					this.customDBProvider.addGroup(con, userid, groupName);
				}
			} else {
			
				insertCreate(user, attributes, attrs, con,request);
			}
			con.commit();
		} catch (Exception e) {
			try {
				if (con != null) con.rollback();
			} catch (SQLException e1) {
				
			}
			throw new ProvisioningException("Could not create user",e);
		} finally {
			if (con != null) {
				try {
					
					con.close();
				} catch (SQLException e) {
					
				}
			}
		}
		
	}

	private void insertCreate(User user, Set<String> attributes,
			Map<String, Attribute> attrs, Connection con, Map<String, Object> request)
			throws SQLException, ProvisioningException {
		
		
		int approvalID = 0;
		
		
		
		
		if (request.containsKey("APPROVAL_ID")) {
			approvalID = (Integer) request.get("APPROVAL_ID");
		}
		
		Workflow workflow = (Workflow) request.get("WORKFLOW");
		
		StringBuffer insert = new StringBuffer();
		insert.append("INSERT INTO ").append(this.userTable).append(" (");
		for (String attr : attributes) {
			if (attrs.get(attr) != null) {
				getFieldName(attr,insert).append(",");
			}
		}
		
		
		
		insert.setLength(insert.length() - 1);
		insert.append(") values (");
		for (String attr : attributes) {
			if (attrs.get(attr) != null) {
				insert.append("?,");
			}
		}
		insert.setLength(insert.length() - 1);
		
		insert.append(")");
		
		
		
		
			
			PreparedStatement ps = con.prepareStatement(insert.toString(),Statement.RETURN_GENERATED_KEYS);
			int i = 1;
			
			for (String attr : attributes) {
				if (attrs.get(attr) != null) {
					
					ps.setString(i, attrs.get(attr).getValues().get(0));
					i++;
				}
				
			}
			
			ps.executeUpdate();
			ResultSet rs = ps.getGeneratedKeys();
			
			int id;
			
			if (rs.next()) {
				
				id = rs.getInt(1);
			} else {
				StringBuffer select = new StringBuffer();
				select.append("SELECT ");
				this.getFieldName(this.userPrimaryKey, select).append(" FROM ").append(this.userTable).append(" WHERE ");
				this.getFieldName(this.userName, select).append("=?");
				PreparedStatement getUserId =  con.prepareStatement(select.toString()); //con.prepareStatement( + this.userPrimaryKey + " FROM " + this.userTable + " WHERE " + this.userName + "=?");
				getUserId.setString(1, user.getUserID());
				ResultSet userResult = getUserId.executeQuery();
				userResult.next();
				id = userResult.getInt(this.userPrimaryKey);
				
				userResult.close();
				getUserId.close();
			}
		
			this.cfgMgr.getProvisioningEngine().logAction(this.name,true, ActionType.Add, approvalID, workflow, "userName", user.getUserID());
			
			for (String attr : attributes) {
				if (attrs.get(attr) != null) {
					this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Add, approvalID, workflow, attr, attrs.get(attr).getValues().get(0));
				}
			}
			
			
			if (user.getGroups().size() > 0) {
				switch (this.groupMode) {
					case None : break;
					case One2Many : 
						insert.setLength(0);
						insert.append("INSERT INTO ").append(this.groupTable).append(" (").append(this.groupUserKey).append(",").append(this.groupName).append(") VALUES (?,?)");
						ps = con.prepareStatement(insert.toString());
						
						for (String groupName : user.getGroups()) {
							ps.setInt(1, id);
							ps.setString(2, groupName);
							ps.executeUpdate();
							this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Add, approvalID, workflow, "group", groupName);
						}
						
						
						
						break;
					case Many2Many : many2manySetGroupsCreate(user, insert, con, id,request); break; 
				}
				
			}
	}

	private void many2manySetGroupsCreate(User user, StringBuffer insert,
			Connection con, int id, Map<String, Object> request) throws SQLException, ProvisioningException {
		
		
		int approvalID = 0;
		
		
		
		
		if (request.containsKey("APPROVAL_ID")) {
			approvalID = (Integer) request.get("APPROVAL_ID");
		}
		
		Workflow workflow = (Workflow) request.get("WORKFLOW");
		
		int i;
		ResultSet rs;
		StringBuffer select = new StringBuffer("SELECT ");
		this.getFieldName(this.groupPrimaryKey,select).append(",");
		this.getFieldName(this.groupName,select).append(" FROM ").append(this.groupTable).append(" WHERE ");
		for (String group : user.getGroups()) {
			this.getFieldName(this.groupName,select).append("=? OR ");
		}
		
		select.setLength(select.length() - 3);
		
		PreparedStatement psSearch = con.prepareStatement(select.toString());
		i = 1;
		for (String group : user.getGroups()) {
			psSearch.setString(i, group);
			i++;
		}
		
		rs = psSearch.executeQuery();
		insert.setLength(0);
		insert.append("INSERT INTO ").append(this.groupLinkTable).append(" (");
		this.getFieldName(this.groupGroupKey,insert).append(",");
		this.getFieldName(this.groupUserKey,insert).append(") VALUES (?,?)");
		
		PreparedStatement psExec = con.prepareStatement(insert.toString());
		while (rs.next()) {
			psExec.setInt(1, rs.getInt(this.groupPrimaryKey));
			psExec.setInt(2, id);
			psExec.executeUpdate();
			
			this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Add, approvalID, workflow, "group", rs.getString(this.groupName));
		}
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.provisioning.core.providers.BasicDB#syncUser(com.tremolosecurity.provisioning.core.User, boolean, java.util.Set, java.util.Map)
	 */
	
	@Override
	public void syncUser(User user, boolean addOnly, Set<String> attributes,Map<String,Object> wfrequest)
			throws ProvisioningException {
		User foundUser = null;
		
		
		
		int approvalID = 0;
		
		
		
		
		if (wfrequest.containsKey("APPROVAL_ID")) {
			approvalID = (Integer) wfrequest.get("APPROVAL_ID");
		}
		
		Workflow workflow = (Workflow) wfrequest.get("WORKFLOW");
		
		Set<String> attributesForSearch = new HashSet<String>();
		attributesForSearch.addAll(attributes);
		if (! attributesForSearch.contains(this.userPrimaryKey)) {
			attributesForSearch.add(this.userPrimaryKey);
		}
		
		try {
			//logger.info("Lookin up user : " + user.getUserID());
			foundUser = this.findUser(user.getUserID(), attributesForSearch,wfrequest);
			//logger.info("Lookin up user result : " + foundUser);
			
		} catch (Exception e) {
			//logger.info("Creating new user",e);
			if (logger.isDebugEnabled()) {
				logger.debug("Could not create user",e);
			}
			this.createUser(user, attributes,wfrequest);
			return;
		}
	
		String userID = foundUser.getAttribs().get(this.userPrimaryKey).getValues().get(0);
		int userIDnum = -1;
		
		try {
			userIDnum = Integer.parseInt(userID);
		} catch(Throwable t) {
			//do nothing
		}
		
		Connection con;
		try {
			con = this.ds.getConnection();
		} catch (SQLException e) {
			throw new ProvisioningException("Could not obtain connection",e);
		}
		
		try {
			con.setAutoCommit(false);
			Map<String,Object> request = new HashMap<String,Object>();
			if (this.customDBProvider != null) {
				this.customDBProvider.beginUpdate(con, userIDnum, request);
			}
			
			StringBuffer b = new StringBuffer();
			for (String attrName : attributes) {
				if (user.getAttribs().containsKey(attrName) && foundUser.getAttribs().containsKey(attrName) && ! user.getAttribs().get(attrName).getValues().get(0).equals(foundUser.getAttribs().get(attrName).getValues().get(0))) {
					if (this.customDBProvider != null) {
						this.customDBProvider.updateField(con, userIDnum, request, attrName, foundUser.getAttribs().get(attrName).getValues().get(0), user.getAttribs().get(attrName).getValues().get(0));
						
					} else {
						PreparedStatement ps = updateField(user, con, b, attrName,userID,userIDnum);
					}
					
					this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Replace, approvalID, workflow, attrName, user.getAttribs().get(attrName).getValues().get(0));
					
					
				} else if (user.getAttribs().containsKey(attrName) && ! foundUser.getAttribs().containsKey(attrName)) {
					if (this.customDBProvider != null) {
						this.customDBProvider.updateField(con, userIDnum, request, attrName, null, user.getAttribs().get(attrName).getValues().get(0));
					} else {
						PreparedStatement ps = updateField(user, con, b, attrName,userID,userIDnum);
					}
					
					this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Add, approvalID, workflow, attrName, user.getAttribs().get(attrName).getValues().get(0));
					
				} else if (! user.getAttribs().containsKey(attrName) && foundUser.getAttribs().containsKey(attrName) && ! addOnly) {
					if (this.customDBProvider != null) {
						this.customDBProvider.clearField(con, userIDnum, request, attrName, foundUser.getAttribs().get(attrName).getValues().get(0));
					} else {
						PreparedStatement ps = clearField(user, con, b, attrName,userID,userIDnum);
					}
					
					
					this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Delete, approvalID, workflow, attrName, foundUser.getAttribs().get(attrName).getValues().get(0));
				}
			}
			
			if (this.customDBProvider != null) {
				this.customDBProvider.completeUpdate(con, userIDnum, request);
			}
			
			switch (this.groupMode) {
				case None : break;
				case One2Many : 
					
				b.setLength(0);
				b.append("INSERT INTO ").append(this.groupTable).append(" (");
				this.getFieldName(this.groupName,b).append(",");
				this.getFieldName(this.groupUserKey,b).append(") VALUES (?,?)");
				PreparedStatement ps = con.prepareStatement(b.toString());
				
				for (String groupName : user.getGroups()) {
					if (! foundUser.getGroups().contains(groupName)) {
						ps.setString(1, groupName);
						ps.setInt(2, userIDnum);
						ps.executeUpdate();
						
						this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Add, approvalID, workflow, "group", groupName);
					}
				}
				
				
				b.setLength(0);
				b.append("DELETE FROM ").append(this.groupTable).append(" WHERE ");
				this.getFieldName(this.groupUserKey,b).append("=? AND ");
				this.getFieldName(this.groupName,b).append("=?");
				ps = con.prepareStatement(b.toString());
				
				if (! addOnly) {
					for (String groupName : foundUser.getGroups()) {
						if (! user.getGroups().contains(groupName)) {
							ps.setInt(1, userIDnum);
							ps.setString(2, groupName);
							ps.executeUpdate();
							this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Delete, approvalID, workflow, "group", groupName);
						}
					}
				}
					
					
					break;
				case Many2Many : many2manySyncGroups(user, addOnly, foundUser, userIDnum, con, b,wfrequest); break;
				case Custom : 
					for (String groupName : user.getGroups()) {
						if (! foundUser.getGroups().contains(groupName)) {
							this.customDBProvider.addGroup(con, userIDnum, groupName);
							this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Add, approvalID, workflow, "group", groupName);
						}
					}
					
					if (! addOnly) {
						for (String groupName : foundUser.getGroups()) {
							if (! user.getGroups().contains(groupName)) {
								this.customDBProvider.deleteGroup(con, userIDnum, groupName);
								this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Delete, approvalID, workflow, "group", groupName);
							}
						}
					}
			}
			
			
			con.commit();
			
		} catch (Throwable t) {
			if (con != null) {
				try {
					con.rollback();
				} catch (SQLException e1) {
					//do nothing
				}
			}
			
			throw new ProvisioningException("Could noy sync user",t);
		} finally {
			if (con != null) {
				try {
					con.close();
				} catch (SQLException e) {
					//do nothing
				}
			}
		}
		
		
		
		
	}

	private void many2manySyncGroups(User user, boolean addOnly,
			User foundUser, int userIDnum, Connection con, StringBuffer b, Map<String, Object> request)
			throws SQLException, Exception {
		
		
		int approvalID = 0;
		
		
		
		
		if (request.containsKey("APPROVAL_ID")) {
			approvalID = (Integer) request.get("APPROVAL_ID");
		}
		
		Workflow workflow = (Workflow) request.get("WORKFLOW");
		
		b.setLength(0);
		b.append("SELECT ");
		this.getFieldName(this.groupPrimaryKey,b).append(" FROM ");
		this.getFieldName(this.groupTable,b).append(" WHERE ");
		this.getFieldName(this.groupName,b).append(" = ?");
		PreparedStatement getGroupID = con.prepareStatement(b.toString());

		b.setLength(0);
		b.append("INSERT INTO ").append(this.groupLinkTable).append(" (");
		this.getFieldName(this.groupGroupKey,b).append(",");
		this.getFieldName(this.groupUserKey,b).append(") VALUES (?,?)");
		PreparedStatement addGroup = con.prepareStatement(b.toString());
		
		b.setLength(0);
		b.append("DELETE FROM ").append(this.groupLinkTable).append(" WHERE ");
		this.getFieldName(this.groupGroupKey,b).append("=? AND ");
		this.getFieldName(this.groupUserKey,b).append("=?");
		PreparedStatement delGroup = con.prepareStatement(b.toString());
		
		for (String groupName : user.getGroups()) {
			if (! foundUser.getGroups().contains(groupName)) {
				getGroupID.setString(1, groupName);
				ResultSet rs = getGroupID.executeQuery();
				if (! rs.next()) {
					throw new Exception("Group " + groupName + " does not exist");
				}
				
				int groupID = rs.getInt(this.groupPrimaryKey);
				addGroup.setInt(1, groupID);
				addGroup.setInt(2, userIDnum);
				addGroup.executeUpdate();
				this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Add, approvalID, workflow, "group", groupName);
			}
		}
		
		if (! addOnly) {
			for (String groupName : foundUser.getGroups()) {
				if (! user.getGroups().contains(groupName)) {
					getGroupID.setString(1, groupName);
					ResultSet rs = getGroupID.executeQuery();
					if (! rs.next()) {
						throw new Exception("Group " + groupName + " does not exist");
					}
					int groupID = rs.getInt(this.groupPrimaryKey);
					
					delGroup.setInt(1, groupID);
					delGroup.setInt(2, userIDnum);
					delGroup.executeUpdate();
					this.cfgMgr.getProvisioningEngine().logAction(this.name,false, ActionType.Delete, approvalID, workflow, "group", groupName);
				}
			}
		}
	}

	private PreparedStatement updateField(User user, Connection con,
			StringBuffer b, String attrName, String userID, int userIDnum) throws SQLException {
		b.setLength(0);
		b.append("UPDATE ").append(this.userTable).append(" SET ");
		this.getFieldName(attrName,b).append("=? WHERE ");
		this.getFieldName(this.userPrimaryKey,b).append("=?");
		PreparedStatement ps = con.prepareStatement(b.toString());
		ps.setString(1, user.getAttribs().get(attrName).getValues().get(0));
		if (userIDnum != -1) {
			ps.setInt(2, userIDnum);
		} else {
			ps.setString(2, userID);
		}
		ps.executeUpdate();
		return ps;
	}
	
	private PreparedStatement clearField(User user, Connection con,
			StringBuffer b, String attrName, String userID, int userIDnum) throws SQLException {
		b.setLength(0);
		b.append("UPDATE ").append(this.userTable).append(" SET ").append(attrName).append("= NULL WHERE ");
		this.getFieldName(this.userPrimaryKey,b).append("=?");
		PreparedStatement ps = con.prepareStatement(b.toString());
		
		if (userIDnum != -1) {
			ps.setInt(1, userIDnum);
		} else {
			ps.setString(1, userID);
		}
		ps.executeUpdate();
		return ps;
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.provisioning.core.providers.BasicDB#deleteUser(com.tremolosecurity.provisioning.core.User, java.util.Map)
	 */
	@Override
	
	public void deleteUser(User user,Map<String,Object> request) throws ProvisioningException {
		Connection con = null;
		
		
		int approvalID = 0;
		
		
		Workflow workflow = (Workflow) request.get("WORKFLOW");
		
		if (request.containsKey("APPROVAL_ID")) {
			approvalID = (Integer) request.get("APPROVAL_ID");
		}
		
		
		
		try {
			con = this.ds.getConnection();
			StringBuffer select = new StringBuffer();
			
			if (this.userSQL != null) {
				select.append(this.userSQL.replaceAll("\\%S", this.userPrimaryKey).replaceAll("\\%L", "?"));
			} else {
				select.append("SELECT ");
				this.getFieldName(this.userPrimaryKey,select).append(" FROM ").append(this.userTable).append(" WHERE ");
				this.getFieldName(this.userName,select).append("=?");
			}
			
			
			PreparedStatement ps = con.prepareStatement(select.toString());
			ps.setString(1, user.getUserID());
			ResultSet rs = ps.executeQuery();
			
			if (! rs.next()) {
				throw new ProvisioningException("User not found " + user.getUserID());
			}
			
			int id = rs.getInt(this.userPrimaryKey);
			
			rs.close();
			ps.close();
			
			con.setAutoCommit(false);
			
			if (this.customDBProvider != null) {
				this.customDBProvider.deleteUser(con, id);
			} else {
				select.setLength(0);
				select.append("DELETE FROM ").append(this.userTable).append(" WHERE ");
				this.getFieldName(this.userPrimaryKey,select).append("=?");
				ps = con.prepareStatement(select.toString());
				ps.setInt(1, id);
				ps.executeUpdate();
				
				switch (this.groupMode) {
					case None : break;
					case One2Many : 
						select.setLength(0);
						select.append("DELETE FROM ").append(this.groupTable).append(" WHERE ");
						this.getFieldName(this.groupUserKey,select).append("=?");
						ps = con.prepareStatement(select.toString());
						ps.setInt(1, id);
						ps.executeUpdate();
						break;
					case Many2Many : many2manyDeleteGroups(con, select, id); break; 
				}
			}
			
			
			
			
			
			con.commit();
			
			this.cfgMgr.getProvisioningEngine().logAction(this.name,true, ActionType.Delete, approvalID, workflow, "userName", user.getUserID());
		} catch (Exception e) {
			try {
				con.rollback();
			} catch (SQLException e1) {
				
			}
			
			throw new ProvisioningException("Could not delete user " + user.getUserID(),e);
		} finally {
			if (con != null) {
				
				try {
					con.close();
				} catch (SQLException e) {
					
				}
			}
		}
		
	}

	private void many2manyDeleteGroups(Connection con, StringBuffer select,
			int id) throws SQLException {
		PreparedStatement ps;
		select.setLength(0);
		select.append("DELETE FROM ").append(this.groupLinkTable).append(" WHERE ");
		this.getFieldName(this.groupUserKey,select).append("=?");
		ps = con.prepareStatement(select.toString());
		ps.setInt(1, id);
		ps.executeUpdate();
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.provisioning.core.providers.BasicDB#findUser(java.lang.String, java.util.Set, java.util.Map)
	 */
	
	@Override
	public User findUser(String userID, Set<String> attributes,Map<String,Object> request)
			throws ProvisioningException {
		StringBuffer select = new StringBuffer();
		//select.append("SELECT ").append(this.userPrimaryKey).append(" ");
		StringBuffer selAttrs = new StringBuffer();
		this.getFieldName(this.userPrimaryKey,selAttrs).append(" ");
		
		
		for (String attr : attributes) {
			if (! attr.equalsIgnoreCase("TREMOLO_USER_ID")) {
				selAttrs.append(", ");
				getFieldName(attr,selAttrs).append(" ");
				
			}
		}
		
		if (this.userTable != null) {
			select.append("SELECT ").append(selAttrs).append(" FROM ").append(this.userTable).append(" WHERE ");
			this.getFieldName(this.userName,select).append("=?");
		} else {
			select.append(this.userSQL.replaceAll("\\%S", selAttrs.toString()).replaceAll("\\%L", "?"));
		}
		
		
		
		Connection con = null;
		try {
			con = this.ds.getConnection();
			
			if (logger.isDebugEnabled()) {
				logger.debug("Search string : '" + select.toString() +"'");
			}
			
			PreparedStatement ps = con.prepareStatement(select.toString());
			
			if (logger.isDebugEnabled()) {
				logger.debug("User ID : '" + userID + "'");
			}
			
			ps.setString(1, userID);
			ResultSet rs = ps.executeQuery();
			
			if (! rs.next()) {
				if (! logger.isDebugEnabled()) {
					logger.debug("User not found");
				}
				rs.close();
				ps.close();
				con.close();
				throw new ProvisioningException("User " + userID + " not found");
			}
			
			User user = new User(userID);
			for (String attr : attributes) {
				if (! attr.equalsIgnoreCase("TREMOLO_USER_ID")) {
					String val = rs.getString(attr);
					if (val != null) {
						user.getAttribs().put(attr, new Attribute(attr,val));
					}
				}
			}
			
			int userKey = rs.getInt(this.userPrimaryKey);
			
			rs.close();
			ps.close();
			
			
			switch (this.groupMode) {
				case None: break;
				case One2Many : 
					select.setLength(0);
					select.append("SELECT ");
					getFieldName(this.groupName,select).append(" FROM ").append(this.groupTable).append(" WHERE ");
					this.getFieldName(this.groupUserKey,select).append("=?");
					ps = con.prepareStatement(select.toString());
					ps.setInt(1, userKey);
					rs = ps.executeQuery();
					while (rs.next()) {
						user.getGroups().add(rs.getString(this.groupName));
					}
					
					break;
				case Many2Many : many2manyLoadGroups(select, con, user, userKey); break; 
				case Custom :
					
					if (this.customDBProvider !=null && this.customDBProvider.listCustomGroups()) {
						user.getGroups().addAll(this.customDBProvider.findGroups(con, userKey, request));
					} else {
						select.setLength(0);
						select.append(this.groupSQL.replaceAll("\\%S", this.groupName).replaceAll("\\%I", "?"));
						ps = con.prepareStatement(select.toString());
						ps.setInt(1, userKey);
						rs = ps.executeQuery();
						while (rs.next()) {
							user.getGroups().add(rs.getString(this.groupName));
						}
					}
					
					break;
			}
			
			
			if (logger.isDebugEnabled()) {
				logger.debug("Returning user : '" + user.getUserID() + "'");
			}
			return user;
			
		} catch (Exception e) {
			throw new ProvisioningException("could not find user",e);
		} finally {
			if (con != null) {
				try {
					con.close();
				} catch (SQLException e) {
					
				}
			}
		}
		
	}

	private void many2manyLoadGroups(StringBuffer select, Connection con,
			User user, int userKey) throws SQLException {
		PreparedStatement ps;
		ResultSet rs;
		select.setLength(0);
		select.append("SELECT ");
		getFieldName(this.groupName,select).append(" FROM ").append(this.groupTable).append(" INNER JOIN ").append(this.groupLinkTable).append(" ON ").append(this.groupTable).append(".").append(this.groupPrimaryKey).append("=").append(this.groupLinkTable).append(".").append(this.groupGroupKey).append(" INNER JOIN ").append(this.userTable).append(" ON ").append(this.userTable).append(".").append(this.userPrimaryKey).append("=").append(this.groupLinkTable).append(".").append(this.groupUserKey).append(" WHERE ").append(this.userTable).append(".").append(this.userPrimaryKey).append("=?");
		
		ps = con.prepareStatement(select.toString());
		ps.setInt(1, userKey);
		rs = ps.executeQuery();
		while (rs.next()) {
			
			user.getGroups().add(rs.getString(this.groupName));
		}
		
		rs.close();
		ps.close();
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.provisioning.core.providers.BasicDB#init(java.util.Map, com.tremolosecurity.config.util.ConfigManager, java.lang.String)
	 */
	
	@Override
	public void init(Map<String, Attribute> cfg, ConfigManager cfgMgr,String name)
			throws ProvisioningException {
		
		this.cfgMgr = cfgMgr;
		
		this.name = name;
		
		driver = cfg.get("driver").getValues().get(0);
		logger.info("Driver : '" + driver + "'");
		
		url = cfg.get("url").getValues().get(0);;
		logger.info("URL : " + url);
		user = cfg.get("user").getValues().get(0);;
		logger.info("User : " + user);
		pwd = cfg.get("password").getValues().get(0);;
		logger.info("Password : **********");
		
		
		this.maxCons = Integer.parseInt(cfg.get("maxCons").getValues().get(0));
		logger.info("Max Cons : " + this.maxCons);
		this.maxIdleCons = Integer.parseInt(cfg.get("maxIdleCons").getValues().get(0));
		logger.info("maxIdleCons : " + this.maxIdleCons);
		
		
		DriverAdapterCPDS pool = new DriverAdapterCPDS();
		
		try {
			pool.setDriver(driver);
		} catch (ClassNotFoundException e) {
			throw new ProvisioningException("Could not load JDBC Driver",e);
		}
		pool.setUrl(url);
		pool.setUser(user);
		pool.setPassword(pwd);
		pool.setMaxActive(maxCons);
		pool.setMaxIdle(maxIdleCons);
		
		
		SharedPoolDataSource tds = new SharedPoolDataSource();
        tds.setConnectionPoolDataSource(pool);
        tds.setMaxActive(maxCons);
        tds.setMaxWait(50);
        
        
        if (cfg.get("validationQuery") == null) {
        	this.validationQuery = "SELECT 1";
        } else {
        	this.validationQuery = cfg.get("validationQuery").getValues().get(0);
        }
        
        logger.info("validationQuery : " + this.validationQuery);
        
        tds.setValidationQuery(this.validationQuery);
        
        tds.setTestOnBorrow(true);
        this.ds = tds;
        if (cfg.get("userTable") != null && ! cfg.get("userTable").getValues().get(0).isEmpty()) {
	        this.userTable = cfg.get("userTable").getValues().get(0);
	        logger.info("User table name : " + this.userTable);
        }
        
        if (cfg.get("userSQL") != null && ! cfg.get("userSQL").getValues().get(0).isEmpty()) {
        	this.userSQL = cfg.get("userSQL").getValues().get(0);
        }
        
        this.userPrimaryKey = cfg.get("userPrimaryKey").getValues().get(0);
        logger.info("User Primary Key : " + this.userPrimaryKey);
        this.userName = cfg.get("userName").getValues().get(0); 
        logger.info("User name filed : " + this.userName);
        
        if (cfg.get("beginEscape") != null) {
        	this.beginEscape = cfg.get("beginEscape").getValues().get(0);
        	this.endEscape = cfg.get("endEscape").getValues().get(0);
        } else {
        	this.beginEscape = "";
        	this.endEscape = "";
        }
        
        
        if (cfg.get("groupMode").getValues().get(0).equalsIgnoreCase("None")) {
        	this.groupMode = GroupManagementMode.None;
        } else if (cfg.get("groupMode").getValues().get(0).equalsIgnoreCase("ManyToMany")) {
        	this.groupMode = GroupManagementMode.Many2Many;
        } else if (cfg.get("groupMode").getValues().get(0).equalsIgnoreCase("OneToMany")) {
        	this.groupMode = GroupManagementMode.One2Many;
        } else if (cfg.get("groupMode").getValues().get(0).equalsIgnoreCase("Custom")) {
        	this.groupMode = GroupManagementMode.Custom;
        }
        
        switch (this.groupMode) {
        	case None: break;
        	case Many2Many : 
        		this.groupTable = cfg.get("groupTable").getValues().get(0);
                logger.info("Group Table Name : " + this.groupTable);
                this.groupName = cfg.get("groupName").getValues().get(0);
                logger.info("Group Name : " + this.groupName);
                this.groupUserKey = cfg.get("groupUserKey").getValues().get(0);
                logger.info("Group User Key : " + this.groupUserKey);
                this.groupLinkTable = cfg.get("groupLinkTableName").getValues().get(0);
                logger.info("Group link table name : " + this.groupLinkTable);
                this.groupGroupKey = cfg.get("groupGroupKey").getValues().get(0);
                logger.info("Group group key : " + this.groupGroupKey);
                this.groupPrimaryKey = cfg.get("groupPrimaryKey").getValues().get(0);
                logger.info("Group primary key : " + this.groupPrimaryKey);
                break;
        	case One2Many:
        		this.groupTable = cfg.get("groupTable").getValues().get(0);
                logger.info("Group Table Name : " + this.groupTable);
                this.groupName = cfg.get("groupName").getValues().get(0);
                logger.info("Group Name : " + this.groupName);
                this.groupUserKey = cfg.get("groupUserKey").getValues().get(0);
                logger.info("Group User Key : " + this.groupUserKey);
                break;
        	case Custom:
        		this.groupSQL = cfg.get("groupSQL").getValues().get(0);
        		logger.info("Group SQL : '" + this.groupSQL + "'");
        		this.groupName = cfg.get("groupName").getValues().get(0);
                logger.info("Group Name : " + this.groupName);
                this.groupUserKey = cfg.get("groupUserKey").getValues().get(0);
                logger.info("Group User Key : " + this.groupUserKey);
                break;
                
        }
        
        if (cfg.get("customProvider") != null && ! cfg.get("customProvider").getValues().get(0).isEmpty()) {
        	try {
				this.customDBProvider = (CustomDB) Class.forName(cfg.get("customProvider").getValues().get(0)).newInstance();
			} catch (Exception e) {
				throw new ProvisioningException("Could not initialize",e);
			} 
        }
        
        
        
        
		
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.provisioning.core.providers.BasicDB#setUserPassword(com.tremolosecurity.provisioning.core.User, java.util.Map)
	 */
	
	@Override
	public void setUserPassword(User user,Map<String,Object> request) throws ProvisioningException {
		// TODO Auto-generated method stub
		
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.provisioning.core.providers.BasicDB#getDS()
	 */
	@Override
	public DataSource getDS() {
		return this.ds;
	}

	private StringBuffer getFieldName(String name,StringBuffer sql) {
		if (this.beginEscape.isEmpty()) {
			sql.append(name);
		} else {
			
			sql.append(this.beginEscape).append(name).append(endEscape);
			
		}
		
		return sql;
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.provisioning.core.providers.BasicDB#getGroupTable()
	 */
	@Override
	public String getGroupTable() {
		return groupTable;
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.provisioning.core.providers.BasicDB#getGroupName()
	 */
	@Override
	public String getGroupName() {
		return groupName;
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.provisioning.core.providers.BasicDB#getGroupPrimaryKey()
	 */
	@Override
	public String getGroupPrimaryKey() {
		return groupPrimaryKey;
	}
	
	
}
