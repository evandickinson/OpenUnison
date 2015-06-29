//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.06.28 at 09:33:52 PM EDT 
//


package com.tremolosecurity.config.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * The base configuration type for Unison
 * 
 * <p>Java class for tremoloType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="tremoloType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="applications" type="{http://www.tremolosecurity.com/tremoloConfig}applicationsType"/>
 *         &lt;element name="myvdConfig" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="authMechs" type="{http://www.tremolosecurity.com/tremoloConfig}authMechTypes"/>
 *         &lt;element name="authChains" type="{http://www.tremolosecurity.com/tremoloConfig}authChainsType"/>
 *         &lt;element name="resultGroups" type="{http://www.tremolosecurity.com/tremoloConfig}resultGroupsType"/>
 *         &lt;element name="keyStorePath" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="keyStorePassword" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="provisioning" type="{http://www.tremolosecurity.com/tremoloConfig}provisioningType" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tremoloType", propOrder = {
    "applications",
    "myvdConfig",
    "authMechs",
    "authChains",
    "resultGroups",
    "keyStorePath",
    "keyStorePassword",
    "provisioning"
})
public class TremoloType {

    @XmlElement(required = true)
    protected ApplicationsType applications;
    @XmlElement(required = true)
    protected String myvdConfig;
    @XmlElement(required = true)
    protected AuthMechTypes authMechs;
    @XmlElement(required = true)
    protected AuthChainsType authChains;
    @XmlElement(required = true)
    protected ResultGroupsType resultGroups;
    @XmlElement(required = true)
    protected String keyStorePath;
    @XmlElement(required = true)
    protected String keyStorePassword;
    protected ProvisioningType provisioning;

    /**
     * Gets the value of the applications property.
     * 
     * @return
     *     possible object is
     *     {@link ApplicationsType }
     *     
     */
    public ApplicationsType getApplications() {
        return applications;
    }

    /**
     * Sets the value of the applications property.
     * 
     * @param value
     *     allowed object is
     *     {@link ApplicationsType }
     *     
     */
    public void setApplications(ApplicationsType value) {
        this.applications = value;
    }

    /**
     * Gets the value of the myvdConfig property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMyvdConfig() {
        return myvdConfig;
    }

    /**
     * Sets the value of the myvdConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMyvdConfig(String value) {
        this.myvdConfig = value;
    }

    /**
     * Gets the value of the authMechs property.
     * 
     * @return
     *     possible object is
     *     {@link AuthMechTypes }
     *     
     */
    public AuthMechTypes getAuthMechs() {
        return authMechs;
    }

    /**
     * Sets the value of the authMechs property.
     * 
     * @param value
     *     allowed object is
     *     {@link AuthMechTypes }
     *     
     */
    public void setAuthMechs(AuthMechTypes value) {
        this.authMechs = value;
    }

    /**
     * Gets the value of the authChains property.
     * 
     * @return
     *     possible object is
     *     {@link AuthChainsType }
     *     
     */
    public AuthChainsType getAuthChains() {
        return authChains;
    }

    /**
     * Sets the value of the authChains property.
     * 
     * @param value
     *     allowed object is
     *     {@link AuthChainsType }
     *     
     */
    public void setAuthChains(AuthChainsType value) {
        this.authChains = value;
    }

    /**
     * Gets the value of the resultGroups property.
     * 
     * @return
     *     possible object is
     *     {@link ResultGroupsType }
     *     
     */
    public ResultGroupsType getResultGroups() {
        return resultGroups;
    }

    /**
     * Sets the value of the resultGroups property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResultGroupsType }
     *     
     */
    public void setResultGroups(ResultGroupsType value) {
        this.resultGroups = value;
    }

    /**
     * Gets the value of the keyStorePath property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getKeyStorePath() {
        return keyStorePath;
    }

    /**
     * Sets the value of the keyStorePath property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setKeyStorePath(String value) {
        this.keyStorePath = value;
    }

    /**
     * Gets the value of the keyStorePassword property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    /**
     * Sets the value of the keyStorePassword property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setKeyStorePassword(String value) {
        this.keyStorePassword = value;
    }

    /**
     * Gets the value of the provisioning property.
     * 
     * @return
     *     possible object is
     *     {@link ProvisioningType }
     *     
     */
    public ProvisioningType getProvisioning() {
        return provisioning;
    }

    /**
     * Sets the value of the provisioning property.
     * 
     * @param value
     *     allowed object is
     *     {@link ProvisioningType }
     *     
     */
    public void setProvisioning(ProvisioningType value) {
        this.provisioning = value;
    }

}
