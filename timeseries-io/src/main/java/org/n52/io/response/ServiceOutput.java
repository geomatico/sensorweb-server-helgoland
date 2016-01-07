/**
 * Copyright (C) 2013-2016 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as publishedby the Free
 * Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of the
 * following licenses, the combination of the program with the linked library is
 * not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed under
 * the aforementioned licenses, is permitted by the copyright holders if the
 * distribution is compliant with both the GNU General Public License version 2
 * and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 */
package org.n52.io.response;

public class ServiceOutput extends ParameterOutput {

	private String serviceUrl;

	private String version;
	
	private String type;
	
	private Boolean supportsFirstLatest;
	
	public String getServiceUrl() {
		return serviceUrl;
	}

	public void setServiceUrl(String serviceUrl) {
		this.serviceUrl = serviceUrl;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	public Boolean isSupportsFirstLatest() {
        return supportsFirstLatest;
    }

    public void setSupportsFirstLatest(boolean supportsFirstLatest) {
        this.supportsFirstLatest = Boolean.valueOf(supportsFirstLatest);
    }
    
	@Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ( (serviceUrl == null) ? 0 : serviceUrl.hashCode());
        result = prime * result + ( (type == null) ? 0 : type.hashCode());
        result = prime * result + ( (version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ( !super.equals(obj)) {
            return false;
        }
        if ( ! (obj instanceof ServiceOutput)) {
            return false;
        }
        ServiceOutput other = (ServiceOutput) obj;
        if (serviceUrl == null) {
            if (other.serviceUrl != null) {
                return false;
            }
        }
        else if ( !serviceUrl.equals(other.serviceUrl)) {
            return false;
        }
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        }
        else if ( !type.equals(other.type)) {
            return false;
        }
        if (version == null) {
            if (other.version != null) {
                return false;
            }
        }
        else if ( !version.equals(other.version)) {
            return false;
        }
        return true;
    }
}
