/**
 * Copyright � 2001 The JA-SIG Collaborative.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the JA-SIG Collaborative
 *    (http://www.jasig.org/)."
 *
 * THIS SOFTWARE IS PROVIDED BY THE JA-SIG COLLABORATIVE "AS IS" AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE JA-SIG COLLABORATIVE OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * formatted with JxBeauty (c) johann.langhofer@nextra.at
 */


package  org.jasig.portal.security.provider;

import  java.util.Vector;
import  java.util.Properties;
import  java.io.File;
import  java.io.IOException;
import  java.io.FileInputStream;
import  org.jasig.portal.utils.SmartCache;
import  org.jasig.portal.security.IPerson;
import  org.jasig.portal.security.IRole;
import  org.jasig.portal.security.provider.RoleImpl;
import  org.jasig.portal.security.provider.PersonImpl;
import  org.jasig.portal.security.IAuthorization;
import  org.jasig.portal.security.PortalSecurityException;
import  org.jasig.portal.GenericPortalBean;
import  org.jasig.portal.RdbmServices;
import  org.jasig.portal.services.LogService;


/**
 * @author Bernie Durfee, bdurfee@interactivebusiness.com
 * @version $Revision$
 */
public class ReferenceAuthorization
    implements IAuthorization {
  // Clear the caches every 10 seconds
  protected static SmartCache userRolesCache = new SmartCache(300);
  protected static SmartCache chanRolesCache = new SmartCache(300);
  protected static String s_channelPublisherRole = null;
  static {
    try {
      // Find our properties file and open it
      String filename = GenericPortalBean.getPortalBaseDir() + "properties" + File.separator + "security.properties";
      File propFile = new File(filename);
      Properties securityProps = new Properties();
      try {
        securityProps.load(new FileInputStream(propFile));
        s_channelPublisherRole = securityProps.getProperty("channelPublisherRole");
      } catch (IOException e) {
        LogService.instance().log(LogService.ERROR, new PortalSecurityException(e.getMessage()));
      }
    } catch (Exception e) {
      LogService.instance().log(LogService.ERROR, e);
    }
  }

  /**
   * put your documentation comment here
   */
  public ReferenceAuthorization () {
  }

  // For the publish mechanism to use
  public boolean isUserInRole (IPerson person, IRole role) {
    if (person == null || role == null) {
      return  (false);
    }
    int userId = person.getID();
    if (userId == -1) {
      return  (false);
    }
    try {
      return  GenericPortalBean.getUserLayoutStore().isUserInRole(person, (String)role.getRoleTitle());
    } catch (Exception e) {
      LogService.instance().log(LogService.ERROR, e);
      return  (false);
    }
  }

  /**
   * put your documentation comment here
   * @return
   */
  public Vector getAllRoles () {
    try {
      return  GenericPortalBean.getUserLayoutStore().getAllRoles();
    } catch (Exception e) {
      LogService.instance().log(LogService.ERROR, e);
      return  (null);
    }
  }

  /**
   * put your documentation comment here
   * @param channelID
   * @param roles
   * @return
   */
  public int setChannelRoles (int channelID, Vector roles) {
    // Don't do anything if no roles were passed in
    if (roles == null || roles.size() < 1) {
      return  (0);
    }
    // When changing the channel's roles, we must dump the cache!
    chanRolesCache.remove("" + channelID);
    try {
      return  GenericPortalBean.getUserLayoutStore().setChannelRoles(channelID, roles);
    } catch (Exception e) {
      LogService.instance().log(LogService.ERROR, e);
      return  (-1);
    }
  }

  /**
   * put your documentation comment here
   * @param person
   * @return
   */
  public boolean canUserPublish (IPerson person) {
    if (person == null || person.getID() == -1) {
      // Possibly throw security exception
      return  (false);
    }
    boolean canPublish = isUserInRole(person, new RoleImpl(s_channelPublisherRole));
    return  (canPublish);
  }

  // For the subscribe mechanism to use
  public Vector getAuthorizedChannels (IPerson person) {
    if (person == null || person.getID() == -1) {
      // Possibly throw security exception
      return  (null);
    }
    return  (new Vector());
  }

  /**
   * put your documentation comment here
   * @param person
   * @param channelID
   * @return
   */
  public boolean canUserSubscribe (IPerson person, int channelID) {
    // Fail immediatly if the inputs aren't reasonable
    if (person == null || person.getID() == -1) {
      return  (false);
    }
    // Get all of the channel roles
    Vector chanRoles = getChannelRoles(channelID);
    // If the channel has no roles associated then it's globally accessable
    if (chanRoles.size() == 0) {
      return  (true);
    }
    // Get all of the user's roles
    Vector userRoles = getUserRoles(person);
    // If the user has no roles and the channel does then he can't have access
    if (userRoles.size() == 0) {
      return  (false);
    }
    // Check to see if the user has at least one role in common with the channel
    for (int i = 0; i < userRoles.size(); i++) {
      if (chanRoles.contains(userRoles.elementAt(i))) {
        return  (true);
      }
    }
    return  (false);
  }

  /**
   * put your documentation comment here
   * @param person
   * @param channelID
   * @return
   */
  public boolean canUserRender (IPerson person, int channelID) {
    // If the user can subscribe to a channel, then they can render it!
    return  (canUserSubscribe(person, channelID));
  }

  /**
   * put your documentation comment here
   * @param channelID
   * @return
   */
  public Vector getChannelRoles (int channelID) {
    // Check the smart cache for the roles first
    Vector channelRoles = (Vector)chanRolesCache.get("" + channelID);
    if (channelRoles != null) {
      return  (channelRoles);
    }
    else {
      channelRoles = new Vector();
    }
    try {
      GenericPortalBean.getUserLayoutStore().getChannelRoles(channelRoles, channelID);
      chanRolesCache.put("" + channelID, channelRoles);
      return  (channelRoles);
    } catch (Exception e) {
      LogService.instance().log(LogService.ERROR, e);
      return  (null);
    }
  }

  // For the render mechanism to use
  public Vector getUserRoles (IPerson person) {
    if (person == null || person.getID() == -1) {
      return  (null);
    }
    int userId = person.getID();
    // Check the smart cache for the roles first
    Vector userRoles = (Vector)userRolesCache.get(new Integer(userId));
    if (userRoles != null) {
      return  (userRoles);
    }
    else {
      userRoles = new Vector();
    }
    try {
      GenericPortalBean.getUserLayoutStore().getUserRoles(userRoles, person);
      userRolesCache.put(new Integer(userId), userRoles);
      return  userRoles;
    } catch (Exception e) {
      LogService.instance().log(LogService.ERROR, e);
      return  (null);
    }
  }

  // For the administration mechanism to use
  public void addUserRoles (IPerson person, Vector roles) {
    if (person == null || person.getID() == -1 || roles == null || roles.size() < 1) {
      return;
    }
    try {
      GenericPortalBean.getUserLayoutStore().addUserRoles(person, roles);
    } catch (Exception e) {
      LogService.instance().log(LogService.ERROR, e);
    }
  }

  /**
   * put your documentation comment here
   * @param person
   * @param roles
   */
  public void removeUserRoles (IPerson person, Vector roles) {
    if (person == null || person.getID() == -1 || roles == null || roles.size() < 1) {
      return;
    }
    try {
      GenericPortalBean.getUserLayoutStore().removeUserRoles(person, roles);
      return;
    } catch (Exception e) {
      LogService.instance().log(LogService.ERROR, e);
      return;
    }
  }
}



