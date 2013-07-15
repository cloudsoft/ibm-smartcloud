ibm-smartcloud
--------------

This project contains support for Brooklyn and Cloudsoft AMP to connect to the IBM SmartCloud Enterprise cloud
(the pre-OpenStack edition).  

This code and associated binaries are confidential and proprietary, to be distributed only under a commercial license from Cloudsoft.

### Settings

To use, you will require credentials of the form:

    brooklyn.ibm-smartcloud.identity=a.user@company.com
    brooklyn.ibm-smartcloud.credential=PASSWORD

The following settings are also supported (and in the first two cases, recommended):

    brooklyn.location.named.ibm-sydney-singapore.user=idcuser
    brooklyn.location.named.ibm-sydney-singapore.image=Red Hat Enterprise Linux 6 (64-bit)
    brooklyn.location.named.ibm-sydney-singapore.location=Singapore

### Build

A simple `mvn clean install` will build the library and make it available (locally) to any project which uses it.


