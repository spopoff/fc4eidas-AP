/*
 * 
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 * 
 * http://www.osor.eu/eupl/european-union-public-licence-eupl-v.1.1
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * Licence for the specific language governing permissions and limitations under
 * the Licence.
 */

package eu.eidas.ap.actions;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import eu.eidas.auth.commons.IPersonalAttributeList;
import eu.eidas.auth.commons.EIDASUtil;
import eu.eidas.auth.commons.PersonalAttribute;
import eu.eidas.auth.commons.PersonalAttributeList;
import eu.eidas.auth.commons.EIDASStatusCode;
import eu.eidas.auth.commons.exceptions.InternalErrorEIDASException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attribute Provider example 
 * 
 * @author ricardo.ferreira@multicert.com, renato.portela@multicert.com, luis.felix@multicert.com, hugo.magalhaes@multicert.com 
 *
 */
public class APAction extends ActionSupport{

	private static final long serialVersionUID = 1469868496283189363L;

	/**
	 * Logger object
	 */
	static final Logger logger = LoggerFactory.getLogger(APAction.class.getName());

	private String callbackUrl;
	private String username;
	private String strAttrList;
	
	/**
	 * 
	 * The execute method is called by default when the action is triggered
	 * 
	 * @return
	 *  
	 */
        @Override
	public String execute(){
		logger.debug("[ACTION-START] APAction");

        StringBuilder strBld = new StringBuilder();
        final String ATTRIBUTE_SIGNED_DOC = "signedDoc";
        PersonalAttribute attrSigned = null;

		Properties utilsConfigs = loadConfigs("eidasUtil.properties");
		EIDASUtil.createInstance(utilsConfigs);
		Properties configs = loadConfigs("ap.properties");

		IPersonalAttributeList attrList = new PersonalAttributeList();
		attrList.populate(getStrAttrList());

		String prefix = getUsername(); 
                logger.debug("APAction username="+prefix);
		IPersonalAttributeList personalAttrList = new PersonalAttributeList();
		for(PersonalAttribute pa : attrList){
			String attrName = pa.getName();
			//PersonalAttribute pa = (PersonalAttribute)personalAttributeList.get(attrName);
			String key = prefix+"."+attrName;
			String keyForValue=key;
			String keyHasMultivalue = prefix+"."+attrName+".multivalue";
			int index=1;

			while(true) {
				if (configs.containsKey(keyHasMultivalue) && Boolean.parseBoolean(configs.getProperty(keyHasMultivalue))) {
					keyForValue = key + "." + index;
				}
                                logger.debug("APAction looking for="+keyForValue);
				if (configs.containsKey(keyForValue)) {
					logger.debug("Attribute " + attrName + " found with value " + configs.getProperty(key));

					if (attrName.equals("canonicalResidenceAddress")) {
						logger.debug("Filling " + attrName);
						PersonalAttribute canonicalResidenceAddress = new PersonalAttribute();
						canonicalResidenceAddress.setName("canonicalResidenceAddress");
						canonicalResidenceAddress.setIsRequired(pa.isRequired());
						canonicalResidenceAddress.setStatus(EIDASStatusCode.STATUS_AVAILABLE.toString());
						HashMap<String, String> address = new HashMap<String, String>();
						address.put("countryCodeAddress", configs.getProperty(prefix + "." + attrName + ".countryCodeAddress"));
						address.put("state", configs.getProperty(prefix + "." + attrName + ".state"));
						address.put("municipalityCode", configs.getProperty(prefix + "." + attrName + ".municipalityCode"));
						address.put("town", configs.getProperty(prefix + "." + attrName + ".town"));
						address.put("postalCode", configs.getProperty(prefix + "." + attrName + ".postalCode"));
						address.put("streetName", configs.getProperty(prefix + "." + attrName + ".streetName"));
						address.put("streetNumber", configs.getProperty(prefix + "." + attrName + ".streetNumber"));
						address.put("apartmentNumber", configs.getProperty(prefix + "." + attrName + ".apartmentNumber"));
						canonicalResidenceAddress.setComplexValue(address);
						personalAttrList.put(attrName, canonicalResidenceAddress);
					} else {
						List<String> tmp = pa.getValue()==null?new ArrayList<String>():pa.getValue();
                        String attributeValue=configs.getProperty(keyForValue);
						tmp.add(attributeValue);
                        if(needsTransliteration(attributeValue)){
                            tmp.add(transliterate(attrName));//TODO should transliterate the value, not the name
                        }
						pa.setValue(tmp);
						pa.setStatus(EIDASStatusCode.STATUS_AVAILABLE.toString());

						// Correction of the signedDoc issue causing the rest of the string to be ignored (1/2)
						// This attribute will be added as latest for causing xml string to be closed
						if (ATTRIBUTE_SIGNED_DOC.equals(pa.getName())) {
							attrSigned = pa;
						} else {
							personalAttrList.put(attrName, pa);
						}

						logger.debug("Attribute " + attrName + " updated!");
					}
				} else {
					LOG.debug("[execute] Attriubte not found: " + attrName);
					if(pa.getValue()==null || pa.getValue().isEmpty()) {
						pa.setStatus(EIDASStatusCode.STATUS_NOT_AVAILABLE.toString());
					}

					// Correction of the signedDoc issue causing the rest of the string to be ignored (1/2)
					// This attribute will be added as latest for causing xml string to be closed
					if (ATTRIBUTE_SIGNED_DOC.equals(pa.getName())) {
						attrSigned = pa;
					} else {
						personalAttrList.put(attrName, pa);
					}
					break;
				}
				if (configs.containsKey(keyHasMultivalue)) {
					index++;
				}else{
					break;
				}
			}
		}
        // setStrAttrList(personalAttrList.toString());
        // Correction of the signedDoc issue causing the rest of the string to be ignored (2/2)
        strBld.append(personalAttrList.toString());
        if (attrSigned != null) {
            strBld.append(attrSigned.toString());
        }
        setStrAttrList(strBld.toString());

        logger.trace("Attribute List updated! Returning " + getStrAttrList());
		return Action.SUCCESS;
	}
	
	/**
	 * Loads the configuration file pointed by the given path. 
	 * 
	 * @param path
	 *            Path to the input file
	 * 
	 * @return property The loaded Properties
	 * 
	 * @throws InternalErrorEIDASException
	 *             if the configuration file could not be loaded.
	 */
	private Properties loadConfigs(String path) {
		return EIDASUtil.loadConfigs(path);
	}
	
	public void setCallbackUrl(String url) {
		this.callbackUrl = url;

	}
	
	public String getCallbackUrl() {
		return this.callbackUrl;

	}
	
	public void setUsername(String username) {
		this.username = username;

	}
	
	public String getUsername() {
		return this.username;

	}

	public void setStrAttrList(String strAttrList) {
		this.strAttrList = strAttrList;
	}

	public String getStrAttrList() {
		return this.strAttrList;
	}
	static CharsetEncoder encoder = Charset.forName("ISO-8859-1").newEncoder();

	public static boolean needsTransliteration(String v) {
		return !encoder.canEncode(v);
	}

	private final static String TRANLITERATOR_ID="Latin; NFD; [:Nonspacing Mark:] Remove; NFC;";
	//TODO: uncomment the line above in order to obtain a true transliteration functionality
    // (see also the change in the pom.xml file)
	// private static Transliterator T=Transliterator.getInstance(TRANLITERATOR_ID);
	public static String transliterate(String value){
		return "transliterated "+value;
		//TODO: uncomment the line above in order to obtain a true transliteration functionality
		//return T==null?value:T.transliterate(value);
	}

}
