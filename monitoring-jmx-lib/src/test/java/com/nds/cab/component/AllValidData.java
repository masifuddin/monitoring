/*
 * Copyright (c) NDS Limited 2010.
 * All rights reserved.
 * No part of this program may be reproduced, translated or transmitted,
 * in any form or by any means, electronic, mechanical, photocopying,
 * recording or otherwise, or stored in any retrieval system of any nature,
 * without written permission of the copyright holder.
 */
package com.nds.cab.component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.cisco.oss.foundation.monitoring.RedundancyMode;

public class AllValidData implements AllValidDataMXBean {
    private String name = "ABC";
    private String fullName = "ABC";
    private String commandLine = "java ABC arg1 arg2";
    private String instance = "Backup";
    private String version = "1.2.3.R4";

    public String getCommandLine() {
        return commandLine;
    }

    public String getInstance() {
        return instance;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public RedundancyMode getApplicationMode() {
        return RedundancyMode.StandAlone;
    }

    public Integer getIntWrapper() {
        return Integer.MAX_VALUE;
    }

    public int getInt() {
        return 4;
    }

    public float getFloat() {
        return 5.6F;
    }

    public Float getFloatWrapper() {
        return Float.MAX_VALUE;
    }

    public double getDouble() {
        return 7.8;
    }

    public Double getDoubleWrapper() {
        return Double.MAX_VALUE;
    }

    public Date getDate() {
        return new Date();
    }

    public Severity getEnum() {
        return Severity.Critical;
    }

    public Request getCompositeObject() {
        return new Request("RequestType1");
    }

    public Request getNullObject() {
        return null;
    }

    public Request[] getCompositeObjectArray() {
        Request[] requestArray = new Request[2];
        requestArray[0] = new Request("R1");
        requestArray[1] = new Request("R2");
        return requestArray;
    }

    public Map<Integer, String> getStringMap() {
        HashMap<Integer, String> myMap = new HashMap<Integer, String>();
        myMap.put(1, "One");
        myMap.put(2, "Two");
        return myMap;
    }

    public Map<Integer, Request> getObjectMap() {
        HashMap<Integer, Request> myMap = new HashMap<Integer, Request>();
        Request[] requests = new Request[2];

        requests[0] = new Request("R1");
        requests[1] = new Request("R2");

        myMap.put(1, requests[0]);
        myMap.put(2, requests[1]);

        return myMap;
    }

    @Override
    public String getFullName() {
        return this.fullName;
    }


}
