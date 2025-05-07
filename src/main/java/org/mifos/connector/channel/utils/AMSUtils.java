package org.mifos.connector.channel.utils;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.mifos.connector.common.gsma.dto.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mifos.connector.channel.zeebe.ZeebeVariables.CALLBACK_URL;


@Component
public class AMSUtils {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private AMSProps amsProps;

    List<AMSProps.AMS> ams;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${callback.url.restrict}")
    private boolean restrictCallbackUrl;

    @Value("#{'${callback.url.allowed-domains}'.split(',')}")
    private List<String> allowedDomains;

    public AMSUtils(){
    }

    @PostConstruct
    public List<AMSProps.AMS> postConstruct(){
       ams  =  amsProps.getGroups();
       return ams;
    }
    public String getAMSName(JSONObject body)
    {
        String primaryIdentifierName= body.getJSONObject("primaryIdentifier").getString("key");
        String secondaryIdentifierName = body.getJSONObject("secondaryIdentifier").getString("key");
        String primaryIdentifierVal = body.getJSONObject("primaryIdentifier").getString("value");
        String secondaryIdentifierVal = body.getJSONObject("primaryIdentifier").getString("value");
        String finalAmsVal="value";
        for ( AMSProps.AMS amsIdentifier : postConstruct()) {
            logger.info("KEY VALUE PAIR : " + amsIdentifier.getIdentifier() + " " + amsIdentifier.getValue());
            String identifier = amsIdentifier.getIdentifier();
            if (identifier.equalsIgnoreCase(secondaryIdentifierName)) {
                finalAmsVal = amsIdentifier.getValue();
                logger.info("Assigned from secondary" + finalAmsVal);
                break;
            }
            else if(identifier.equalsIgnoreCase(primaryIdentifierName)){
                finalAmsVal = amsIdentifier.getValue();
                String temp = primaryIdentifierVal;
                primaryIdentifierVal = secondaryIdentifierVal;
                secondaryIdentifierVal = temp;
                logger.info("Assigned from primary" + finalAmsVal);
                break;
            }
            else {
                if(identifier.equalsIgnoreCase("default")){
                    finalAmsVal = amsIdentifier.getDefaultValue();
                    logger.info("Assigned default from secondary" + finalAmsVal);
                }
            }
        }//end for loop
        logger.info("Identifier name and value {} : {} ",primaryIdentifierName,primaryIdentifierVal);
        return finalAmsVal;
    }

    public Map<String, Object> setZeebeVariables(List<CustomData> customData, String timer) throws JsonProcessingException {
        Map<String,Object>variables=new HashMap<>();
        for(CustomData obj:customData){
            String key=obj.getKey();
            Object value=obj.getValue();
            variables.put(key,value);
        }
        // Also publishing custom data list
        String customDataToString=objectMapper.writeValueAsString(customData);
        if(!customData.isEmpty()){
            variables.put("customData",customDataToString);
        }
        variables.put("timer",timer);
        return variables;
    }

    public Object getOrDefault(JSONObject body, String value, Object defaultValue) {
        return body.has(value) ? body.get(value) : defaultValue;
    }

    /**
     * Adds the callback URL to the variables map if it is present in the custom data string.
     * @param customDataString the custom data string in JSON array format
     * @param variables the map to which the callback URL will be added
     */
    public void addCallbackUrlToVariables(String customDataString, Map<String, Object> variables) {
        Optional<String> callbackUrl = extractCallbackUrlFromCustomData(customDataString);
        if(callbackUrl.isPresent()) {
            String url = callbackUrl.get().replaceAll("\\s+", "");
            if (!isValidCallbackUrl(url)) {
                throw new IllegalArgumentException("Invalid callbackUrl: " + url);
            }
            variables.put(CALLBACK_URL, url);
        }
    }

    /**
     * Extracts the callback URL from the custom data string.
     * @param customDataString the custom data string in JSON array format
     * @return an Optional containing the callback URL if found, or an empty Optional if not found
     */
    private Optional<String> extractCallbackUrlFromCustomData(String customDataString) {
        if (StringUtils.isBlank(customDataString)) {
            return Optional.empty();
        }
        try {
            JsonNode rootNode = objectMapper.readTree(customDataString);
            if (!rootNode.isArray()) {
                return Optional.empty();
            }
            for (JsonNode node : rootNode) {
                JsonNode keyNode = node.get("key");
                if (keyNode != null && CALLBACK_URL.equalsIgnoreCase(keyNode.asText())) {
                    JsonNode valueNode = node.get("value");
                    if (valueNode != null && !valueNode.asText().isBlank()) {
                        return Optional.of(valueNode.asText());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error while extracting {} from custom data: {}", CALLBACK_URL, customDataString, e);
        }
        return Optional.empty();
    }

    /**
     * Validates the callback URL.
     * @param url the callback URL to validate
     * @return true if the URL is valid, false otherwise
     */
    private boolean isValidCallbackUrl(String url) {
        try {
            URI uri = new URI(url);

            // Basic structure check
            if (!uri.isAbsolute() || uri.getHost() == null) {
                return false;
            }

            String scheme = uri.getScheme().toLowerCase();
            String host = uri.getHost().toLowerCase();

            // Scheme must be http or https
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }

            if (restrictCallbackUrl) {
                // Enforce HTTPS and domain restriction
                if (!scheme.equals("https")) {
                    return false;
                }
                if (!allowAllDomains()) {
                    return allowedDomains.stream().anyMatch(
                        domain -> host.equals(domain) || host.endsWith("." + domain)
                    );
                }
            }
            return true;

        } catch (Exception e) {
            logger.error("Error validating callback URL: {}", url, e);
            return false;
        }
    }

    /**
     * Checks if all domains are allowed.
     * @return true if all domains are allowed, false otherwise
     */
    private boolean allowAllDomains()  {
        return allowedDomains == null
            || allowedDomains.isEmpty()
            || (allowedDomains.size() == 1 && allowedDomains.get(0).equals(""))
            || allowedDomains.contains("*");
    }
}




