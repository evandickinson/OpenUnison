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


package com.tremolosecurity.config.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.Certificate;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.crypto.SecretKey;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.http.HttpHost;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.logging.log4j.Logger;


import net.sourceforge.myvd.chain.InterceptorChain;
import net.sourceforge.myvd.server.Server;
import net.sourceforge.myvd.server.ServerCore;

import com.novell.ldap.LDAPException;
import com.tremolosecurity.config.ssl.TremoloX509KeyManager;
import com.tremolosecurity.config.xml.ApplicationType;
import com.tremolosecurity.config.xml.AuthChainType;
import com.tremolosecurity.config.xml.AuthMechType;
import com.tremolosecurity.config.xml.CustomAzRuleType;
import com.tremolosecurity.config.xml.ParamType;
import com.tremolosecurity.config.xml.TremoloType;
import com.tremolosecurity.config.xml.MechanismType;
import com.tremolosecurity.config.xml.ResultGroupType;
import com.tremolosecurity.config.xml.UrlType;
import com.tremolosecurity.provisioning.core.ProvisioningEngine;
import com.tremolosecurity.provisioning.core.ProvisioningEngineImpl;
import com.tremolosecurity.provisioning.core.ProvisioningException;
import com.tremolosecurity.proxy.auth.AnonAuth;
import com.tremolosecurity.proxy.auth.AuthMechanism;
import com.tremolosecurity.proxy.auth.sys.AuthManager;
import com.tremolosecurity.proxy.auth.sys.AuthManagerImpl;
import com.tremolosecurity.proxy.az.CustomAuthorization;
import com.tremolosecurity.proxy.myvd.MyVDConnection;
import com.tremolosecurity.proxy.ssl.TremoloTrustManager;
import com.tremolosecurity.saml.Attribute;
import com.tremolosecurity.server.StopableThread;



public abstract class UnisonConfigManagerImpl implements ConfigManager, UnisonConfigManager {
	
	static Logger logger = org.apache.logging.log4j.LogManager.getLogger(UnisonConfigManagerImpl.class);
	
	private String authPath;
	private String authForms;
	private String authIdP;
	
	private String paasUserPrinicipalAttribute;
	private String paasRoleAttribute;
	
	
	
	static ConfigManager instance;
	
	private AuthManager authMgr;
	
	TremoloType cfg;
	HashMap<String,ArrayList<UrlHolder>> byHost;
	HashMap<String,UrlHolder> cache;
	protected ServerCore myvd;
	protected MyVDConnection con;
	
	HashMap<String, AuthChainType> authChains;
	HashMap<String, MechanismType> authMechs;
	HashMap<String, ResultGroupType> resGroups;
	HashMap<String, ApplicationType> apps;
	
	HashMap<String,AuthMechanism> mechs;
	
	protected KeyStore ks;
	protected KeyManagerFactory kmf;
	
	ProvisioningEngine provEnvgine;

	protected String configXML;

	protected ServletContext ctx;
	
	private List<StopableThread> threads;
	

	
	
	
	private ArrayList<ReloadNotification> notifiers;

	

	private AuthChainType anonAct;

	private AnonAuth anonAuthMech;

	private String ctxPath;

	private RequestConfig globalHttpClientConfig;

	private Registry<ConnectionSocketFactory> httpClientRegistry;

	private String name;
	
	private HashMap<String,CustomAuthorization> customAzRules;

	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getConfigXmlPath()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getConfigXmlPath()
	 */
	@Override
	
	public String getConfigXmlPath() {
		return this.configXML;
	}

	private void initSSL() throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, FileNotFoundException, IOException {
		if (this.getKeyManagerFactory() == null) {
			return;
		}
		
		KeyStore cacerts = KeyStore.getInstance(KeyStore.getDefaultType());
		

		
		
		
		String cacertsPath = System.getProperty("javax.net.ssl.trustStore");
		if (cacertsPath == null) {
			cacertsPath = System.getProperty("java.home") + "/lib/security/cacerts";
		}
		
		cacerts.load(new FileInputStream(cacertsPath), null);
		
		Enumeration<String> enumer = cacerts.aliases();
		while (enumer.hasMoreElements()) {
			String alias = enumer.nextElement();
			java.security.cert.Certificate cert = cacerts.getCertificate(alias);
			this.ks.setCertificateEntry(alias, cert);
		}
		
		SSLContext sslctx = SSLContexts.custom().loadTrustMaterial(this.ks).loadKeyMaterial(this.ks,this.cfg.getKeyStorePassword().toCharArray()).build();
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslctx,SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		
		PlainConnectionSocketFactory sf = PlainConnectionSocketFactory.getSocketFactory();
		httpClientRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
		        .register("http", sf)
		        .register("https", sslsf)
		        .build();
		
		globalHttpClientConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).setRedirectsEnabled(false).setAuthenticationEnabled(false).build();
		

	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getAuthMechs()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getAuthMechs()
	 */
	
	@Override
	public HashMap<String, MechanismType> getAuthMechs() {
		return authMechs;
	}


	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getCfg()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getCfg()
	 */
	
	@Override
	public TremoloType getCfg() {
		return cfg;
	}



	
	/**
	 * @param configXML
	 * @param path
	 * @throws Exception
	 */
	public UnisonConfigManagerImpl(String configXML,ServletContext ctx,String name) throws Exception {
		this.configXML = configXML;
		this.ctx = ctx;
		this.name = name;

		
		this.notifiers = new ArrayList<ReloadNotification>();
		
		if (ctx != null) {
			if (ctx.getContextPath().equalsIgnoreCase("/")) {
				this.authPath = "/auth/";
			} else {
				this.authPath = ctx.getContextPath() + "/auth/";
			}
		} else {
			this.authPath =  "/auth/";
		}
		this.authForms = this.authPath + "forms/";
		this.authIdP = this.authPath + "idp/";
		
		if (this.ctx != null) {
			this.ctxPath = ctx.getContextPath();
		} else {
			this.ctxPath = "/";
		}
		
	}
	

	public abstract JAXBElement<TremoloType> loadUnisonConfiguration(Unmarshaller unmarshaller) throws Exception;
	

	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#initialize()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#initialize()
	 */
	
	@Override
	public void initialize(String name)
			throws JAXBException, Exception, IOException,
			FileNotFoundException, InstantiationException,
			IllegalAccessException, ClassNotFoundException, LDAPException,
			KeyStoreException, NoSuchAlgorithmException, CertificateException,
			ProvisioningException {
		JAXBContext jc = JAXBContext.newInstance("com.tremolosecurity.config.xml");
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		
		String path = configXML;
		this.threads = new ArrayList<StopableThread>();
		
		
		
		//path = path.substring(path.lastIndexOf('/') - 1);
		//path = path.substring(path.lastIndexOf('/') - 1);
		
		path = path.substring(0,path.lastIndexOf('/'));

		
		
		
		JAXBElement<TremoloType> autoidmcfg = this.loadUnisonConfiguration(unmarshaller);
		this.cfg = autoidmcfg.getValue();

		this.byHost = new HashMap<String,ArrayList<UrlHolder>>();
		this.cache = new HashMap<String,UrlHolder>();
		
		
		
		
		
		
		String myVdPath = cfg.getMyvdConfig();
		
		
		this.loadKeystore(path,myVdPath);
		
		
		
		this.initSSL();
		
		this.loadMyVD(path, myVdPath);
		
		this.customAzRules = new HashMap<String,CustomAuthorization>();
		if (this.cfg.getCustomAzRules() != null) {
			for (CustomAzRuleType azrule : this.cfg.getCustomAzRules().getAzRule()) {
				HashMap<String,Attribute> azCfg = new HashMap<String,Attribute>();
				for (ParamType pt : azrule.getParams()) {
					Attribute attr = azCfg.get(pt.getName());
					if (attr == null) {
						attr = new Attribute(pt.getName());
						azCfg.put(pt.getName(), attr);
					}
					
					attr.getValues().add(pt.getValue());
					
				}
				
				CustomAuthorization cuz = (CustomAuthorization) Class.forName(azrule.getClassName()).newInstance();
				cuz.init(azCfg);
				
				this.customAzRules.put(azrule.getName(), cuz);
			}
		}
		
		
		loadApplicationObjects();
		
		
		
		this.authChains = new HashMap<String,AuthChainType>();
		
		if (cfg.getAuthChains() != null) {
			Iterator<AuthChainType> itac = cfg.getAuthChains().getChain().iterator();
			while (itac.hasNext()) {
				AuthChainType ac = itac.next();
				this.authChains.put(ac.getName(),ac);
			}
		}
		
		this.authMechs = new HashMap<String,MechanismType>();
		
		if (cfg.getAuthMechs() != null) {
			Iterator<MechanismType> itmt = cfg.getAuthMechs().getMechanism().iterator();
			while (itmt.hasNext()) {
				MechanismType mt = itmt.next();
				authMechs.put(mt.getName(), mt);
			}
		}
		
		this.resGroups = new HashMap<String,ResultGroupType>();
		
		if (cfg.getResultGroups() != null) {
			Iterator<ResultGroupType> itrgt = cfg.getResultGroups().getResultGroup().iterator();
			while (itrgt.hasNext()) {
				ResultGroupType rgt = itrgt.next();
				this.resGroups.put(rgt.getName(), rgt);
			}
		}
		
		
		
		this.apps = new HashMap<String,ApplicationType>();
		Iterator<ApplicationType> itApp = cfg.getApplications().getApplication().iterator();
		while (itApp.hasNext()) {
			ApplicationType app = itApp.next();
			this.apps.put(app.getName(), app);
		}
		
		
		
		
		
		
		this.provEnvgine = new ProvisioningEngineImpl(this);
		this.provEnvgine.initWorkFlows();
		this.provEnvgine.initMessageConsumers();
		this.provEnvgine.initScheduler();
		this.provEnvgine.initListeners();
		this.provEnvgine.clearDLQ();
		
		
		
		
		
	}


	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getAuthChains()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getAuthChains()
	 */
	
	public abstract void loadKeystore(String path,String myVdPath) throws Exception;

	@Override
	public HashMap<String, AuthChainType> getAuthChains() {
		return authChains;
	}


	private void loadApplicationObjects() throws Exception {
		Iterator<ApplicationType> apps = this.cfg.getApplications().getApplication().iterator();
		
		while (apps.hasNext()) {
			ApplicationType app = apps.next();
			Iterator<UrlType> urls = app.getUrls().getUrl().iterator();
			
			while (urls.hasNext()) {
				UrlType url = urls.next();
				
				Iterator<String> hosts = url.getHost().iterator();
				
				while (hosts.hasNext()) {
					String host = hosts.next();
					ArrayList<UrlHolder> hostUrls = this.byHost.get(host);
					if (hostUrls == null) {
						hostUrls = new ArrayList<UrlHolder>();
						this.byHost.put(host, hostUrls);
					}
					
					hostUrls.add(new UrlHolder(app,url,this));
				}
			}
			
			
		}
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#findURL(java.lang.String)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#findURL(java.lang.String)
	 */
	
	@Override
	public UrlHolder findURL(String url) throws Exception {
		
		
		
		UrlHolder ret = this.cache.get(url);
		if (ret != null) {
			return ret;
		}
		
		URL urlObj =  new URL(url);
		
		if (urlObj.getPath().startsWith(this.authPath) && ! urlObj.getPath().startsWith("/auth/idp/")) {
			return null;
		}
		
		String host = urlObj.getHost();
		ArrayList<UrlHolder> urls = this.byHost.get(host);
		
		if (urls == null) {
			
		}
		
		ArrayList<UrlHolder> tmpList = new ArrayList<UrlHolder>();
		if (urls != null) {
			tmpList.addAll(urls);
		}
		
		urls = this.byHost.get("*");
		if (urls != null) {
			tmpList.addAll(urls);
		}
		
		ret = null;
		
		Iterator<UrlHolder> holders = tmpList.iterator();
		while (holders.hasNext()) {
			UrlHolder holder = holders.next();
			if (holder.getUrl().isRegex()) {
				if (holder.getPattern().matcher(urlObj.getPath()).matches()) {
					if (ret != null && ret.getWeight() < holder.getWeight()) {
						ret = holder;
					} else if (ret == null) {
						ret = holder;
					}
				}
			} else {
				
				if (ret != null) {
					
				}
				if (urlObj.getPath().startsWith( holder.getUrl().getUri())) {
					if (ret != null && ((ret.getWeight() < holder.getWeight()) || ((ret.getWeight() == holder.getWeight()) &&  (ret.getUrl().getUri().length() < holder.getUrl().getUri().length())))) {
						ret = holder;
					} else if (ret == null) {
						ret = holder;
					}
				}
			}
		}
		
		return ret;
		
	}


	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getResultGroup(java.lang.String)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getResultGroup(java.lang.String)
	 */
	
	@Override
	public ResultGroupType getResultGroup(String name) {
		return this.resGroups.get(name);
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getMyVD()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getMyVD()
	 */
	
	@Override
	public MyVDConnection getMyVD() {
		return con;
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getSecretKey(java.lang.String)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getSecretKey(java.lang.String)
	 */
	
	@Override
	public SecretKey getSecretKey(String alias)  {
		try {
			return (SecretKey) this.ks.getKey(alias, this.cfg.getKeyStorePassword().toCharArray());
		} catch (Throwable t) {
			logger.error("Could not load secret key", t);
			return null;
		}
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getApp(java.lang.String)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getApp(java.lang.String)
	 */
	
	@Override
	public ApplicationType getApp(String name) {
		return this.apps.get(name);
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#loadFilters()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#loadFilters()
	 */
	
	@Override
	public void loadFilters() {
		Iterator<ArrayList<UrlHolder>> itl = this.byHost.values().iterator();
		while (itl.hasNext()) {
			ArrayList<UrlHolder> lst = itl.next();
			Iterator<UrlHolder> it = lst.iterator();
			while (it.hasNext()) {
				try {
					it.next().init();
				} catch (Exception e) {
					logger.error("Could not initialize filter",e);
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#loadAuthMechs()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#loadAuthMechs()
	 */
	
	@Override
	public void loadAuthMechs() throws ServletException {
		try {
			this.mechs = new HashMap<String,AuthMechanism>();
			
			//UnisonConfigManagerImpl tremoloCfg = (UnisonConfigManagerImpl) ctx.getAttribute(ConfigFilter.TREMOLO_CONFIG);
			if (getCfg().getAuthMechs() != null) {
				Iterator<MechanismType> mechs = getCfg().getAuthMechs().getMechanism().iterator();
				
				while (mechs.hasNext()) {
					MechanismType mt = mechs.next();
					
					AuthMechanism authMech = (AuthMechanism) Class.forName(mt.getClassName().trim()).newInstance();
					
					HashMap<String,Attribute> attrs = new HashMap<String,Attribute>();
					Iterator<ParamType> params = mt.getInit().getParam().iterator();
					
					while (params.hasNext()) {
						ParamType pt = params.next();
						Attribute attr = attrs.get(pt.getName());
						if (attr == null) {
							attr = new Attribute(pt.getName());
							attrs.put(pt.getName(),attr);
						}
						attr.getValues().add(pt.getValue());
					}
					
					authMech.init(ctx, attrs);
					
					if (this.ctxPath.equalsIgnoreCase("/")) {
						this.mechs.put(mt.getUri(), authMech);
					} else {
						this.mechs.put(this.ctxPath +  mt.getUri(), authMech);
					}
					
					
				}
			}
		} catch (Exception e) {
			throw new ServletException("Could not initialize Auth Mechanism Filter",e);
		}
		
		for (String key : this.authChains.keySet()) {
			AuthChainType act = this.authChains.get(key);
			if (act.getLevel() == 0) {
				this.anonAct = act;
				String mechName = act.getAuthMech().get(0).getName();
				this.anonAuthMech =  (AnonAuth) this.getAuthMech(this.authMechs.get(mechName).getUri());
			}
		}
		
		
		if (this.anonAuthMech == null) {
			this.anonAct = new AuthChainType();
			this.anonAct.setFinishOnRequiredSucess(true);
			this.anonAct.setLevel(0);
			this.anonAct.setName("anon");
			
			this.anonAuthMech = new AnonAuth();
			
		}
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getAuthMech(java.lang.String)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getAuthMech(java.lang.String)
	 */
	
	@Override
	public AuthMechanism getAuthMech(String uri) {
		return this.mechs.get(uri);
	}
	
	/*public static UnisonConfigManagerImpl getConfigManager() {
		return instance;
	}
	
	public static void init(String configXML,String configPath) throws Exception {
		instance = new UnisonConfigManagerImpl(configXML,configPath,null);
		instance.loadFilters();
	}
	
	public static void init(String configXML,ServletContext ctx) throws Exception {
		instance = new UnisonConfigManagerImpl(configXML,null,ctx);
		instance.loadFilters();
	}*/


	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getCertificate(java.lang.String)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getCertificate(java.lang.String)
	 */
	
	@Override
	public X509Certificate getCertificate(String alias) {
		try {
			return (X509Certificate) this.ks.getCertificate(alias);
		} catch (Throwable t) {
			logger.error("Could not load certificate " + alias,t);
			return null;
		}
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getProvisioningEngine()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getProvisioningEngine()
	 */
	
	@Override
	public ProvisioningEngine getProvisioningEngine() {
		return this.provEnvgine;
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#reloadConfig()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#reloadConfig()
	 */
	
	@Override
	public void reloadConfig() throws Exception {
		synchronized (this) {
			this.clearThreads();
			this.initialize(this.name);
			this.loadFilters();
			this.loadAuthMechs();
			this.notifyReload();
		}
	}


	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getPrivateKey(java.lang.String)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getPrivateKey(java.lang.String)
	 */
	
	@Override
	public PrivateKey getPrivateKey(String alias) {
		try {
			return (PrivateKey) this.ks.getKey(alias,this.cfg.getKeyStorePassword().toCharArray());
		} catch (Throwable t) {
			logger.error("Could not load certificate " + alias,t);
			return null;
		}
	}
	
	

	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getKeyStore()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getKeyStore()
	 */
	
	@Override
	public KeyStore getKeyStore() {
		return this.ks;
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getKeyManagerFactory()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getKeyManagerFactory()
	 */
	@Override
	
	public KeyManagerFactory getKeyManagerFactory() {
		return this.kmf;
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#addThread(com.tremolosecurity.server.StopableThread)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#addThread(com.tremolosecurity.server.StopableThread)
	 */
	
	@Override
	public void addThread(StopableThread r) {
		this.threads.add(r);
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#clearThreads()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#clearThreads()
	 */
	
	@Override
	public void clearThreads() {
		for (StopableThread r : this.threads) {
			synchronized (r) {
				r.stop();
				r.notify();
			}
		}
		
		this.threads.clear();
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getCm()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getCm()
	 */
	


	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getParams()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getParams()
	 */
	

	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#addReloadNotifier(com.tremolosecurity.config.util.ReloadNotification)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#addReloadNotifier(com.tremolosecurity.config.util.ReloadNotification)
	 */
	
	@Override
	public void addReloadNotifier(ReloadNotification notifier) {
		this.notifiers.add(notifier);
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#notifyReload()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#notifyReload()
	 */
	
	@Override
	public void notifyReload() {
		for (ReloadNotification rn : this.notifiers) {
			rn.reload();
		}
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#isForceToSSL()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#isForceToSSL()
	 */
	
	@Override
	public abstract boolean isForceToSSL() ;

	

	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getOpenPort()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getOpenPort()
	 */
	
	@Override
	public abstract int getOpenPort() ;

	

	
	public abstract int getSecurePort() ;
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getExternalOpenPort()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getExternalOpenPort()
	 */
	
	@Override
	public abstract int getExternalOpenPort() ;
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getExternalSecurePort()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getExternalSecurePort()
	 */
	
	@Override
	public abstract int getExternalSecurePort();

	

	

	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#createAnonUser(javax.servlet.http.HttpSession)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#createAnonUser(javax.servlet.http.HttpSession)
	 */
	
	@Override
	public void createAnonUser(HttpSession sharedSession) {
		this.anonAuthMech.createSession(sharedSession, anonAct);
		
		
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getAuthPath()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getAuthPath()
	 */
	
	@Override
	public String getAuthPath() {
		return this.authPath;
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getAuthFormsPath()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getAuthFormsPath()
	 */
	
	@Override
	public String getAuthFormsPath() {
		return this.authForms;
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getAuthIdPPath()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getAuthIdPPath()
	 */
	
	@Override
	public String getAuthIdPPath() {
		return this.authIdP;
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getPaasUserPrinicipalAttribute()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getPaasUserPrinicipalAttribute()
	 */
	
	@Override
	public String getPaasUserPrinicipalAttribute() {
		return paasUserPrinicipalAttribute;
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#setPaasUserPrinicipalAttribute(java.lang.String)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#setPaasUserPrinicipalAttribute(java.lang.String)
	 */
	
	@Override
	public void setPaasUserPrinicipalAttribute(
			String paasUserPrinicipalAttribute) {
		this.paasUserPrinicipalAttribute = paasUserPrinicipalAttribute;
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getPaasRoleAttribute()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getPaasRoleAttribute()
	 */
	
	@Override
	public String getPaasRoleAttribute() {
		return paasRoleAttribute;
	}

	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#setPaasRoleAttribute(java.lang.String)
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#setPaasRoleAttribute(java.lang.String)
	 */
	
	@Override
	public void setPaasRoleAttribute(String paasRoleAttribute) {
		this.paasRoleAttribute = paasRoleAttribute;
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getContextPath()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getContextPath()
	 */
	
	@Override
	public String getContextPath() {
		return this.ctxPath;
	}
	
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.ConfigManager#getContext()
	 */
	/* (non-Javadoc)
	 * @see com.tremolosecurity.config.util.UnisonConfigManager#getContext()
	 */
	
	@Override
	public ServletContext getContext() {
		return this.ctx;
	}

	@Override
	public AuthManager getAuthManager() {
		if (this.authMgr == null) {
			this.authMgr = new AuthManagerImpl();
		}
		
		return this.authMgr;
	}

	@Override
	public RequestConfig getGlobalHttpClientConfig() {
		return this.globalHttpClientConfig;
	}

	@Override
	public Registry<ConnectionSocketFactory> getHttpClientSocketRegistry() {
		return this.httpClientRegistry;
	}

	public abstract void loadMyVD(String path, String myVdPath) throws Exception;

	@Override
	public Map<String, CustomAuthorization> getCustomAuthorizations() {
		return this.customAzRules;
	}
	
	
	
	
}
