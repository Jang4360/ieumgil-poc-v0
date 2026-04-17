package com.example.poc.transit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "poc.transit-mixed")
public class TransitMixedRoutingProperties {

	private boolean enabled = true;
	private int maxCandidates = 3;
	private String odsayApiBaseUrl;
	private String odsayApiKey;
	private String odsayServerIp;
	private String busanBimsApiBaseUrl;
	private String busanBimsServiceKeyEncoding;
	private String busanBimsServiceKeyDecoding;
	private String busanSubwayOperationApiBaseUrl;
	private String busanSubwayOperationApiPath;
	private String busanSubwayOperationServiceKeyEncoding;
	private String busanSubwayOperationServiceKeyDecoding;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getMaxCandidates() {
		return maxCandidates;
	}

	public void setMaxCandidates(int maxCandidates) {
		this.maxCandidates = maxCandidates;
	}

	public String getOdsayApiBaseUrl() {
		return odsayApiBaseUrl;
	}

	public void setOdsayApiBaseUrl(String odsayApiBaseUrl) {
		this.odsayApiBaseUrl = odsayApiBaseUrl;
	}

	public String getOdsayApiKey() {
		return odsayApiKey;
	}

	public void setOdsayApiKey(String odsayApiKey) {
		this.odsayApiKey = odsayApiKey;
	}

	public String getOdsayServerIp() {
		return odsayServerIp;
	}

	public void setOdsayServerIp(String odsayServerIp) {
		this.odsayServerIp = odsayServerIp;
	}

	public String getBusanBimsApiBaseUrl() {
		return busanBimsApiBaseUrl;
	}

	public void setBusanBimsApiBaseUrl(String busanBimsApiBaseUrl) {
		this.busanBimsApiBaseUrl = busanBimsApiBaseUrl;
	}

	public String getBusanBimsServiceKeyEncoding() {
		return busanBimsServiceKeyEncoding;
	}

	public void setBusanBimsServiceKeyEncoding(String busanBimsServiceKeyEncoding) {
		this.busanBimsServiceKeyEncoding = busanBimsServiceKeyEncoding;
	}

	public String getBusanBimsServiceKeyDecoding() {
		return busanBimsServiceKeyDecoding;
	}

	public void setBusanBimsServiceKeyDecoding(String busanBimsServiceKeyDecoding) {
		this.busanBimsServiceKeyDecoding = busanBimsServiceKeyDecoding;
	}

	public String getBusanSubwayOperationApiBaseUrl() {
		return busanSubwayOperationApiBaseUrl;
	}

	public void setBusanSubwayOperationApiBaseUrl(String busanSubwayOperationApiBaseUrl) {
		this.busanSubwayOperationApiBaseUrl = busanSubwayOperationApiBaseUrl;
	}

	public String getBusanSubwayOperationApiPath() {
		return busanSubwayOperationApiPath;
	}

	public void setBusanSubwayOperationApiPath(String busanSubwayOperationApiPath) {
		this.busanSubwayOperationApiPath = busanSubwayOperationApiPath;
	}

	public String getBusanSubwayOperationServiceKeyEncoding() {
		return busanSubwayOperationServiceKeyEncoding;
	}

	public void setBusanSubwayOperationServiceKeyEncoding(String busanSubwayOperationServiceKeyEncoding) {
		this.busanSubwayOperationServiceKeyEncoding = busanSubwayOperationServiceKeyEncoding;
	}

	public String getBusanSubwayOperationServiceKeyDecoding() {
		return busanSubwayOperationServiceKeyDecoding;
	}

	public void setBusanSubwayOperationServiceKeyDecoding(String busanSubwayOperationServiceKeyDecoding) {
		this.busanSubwayOperationServiceKeyDecoding = busanSubwayOperationServiceKeyDecoding;
	}
}
