
Cocop.AmqpMathToolConnector v.2.0.0
===================================

---

<img src="logos.png" alt="COCOP and EU" style="display:block;margin-right:auto" />

COCOP - Coordinating Optimisation of Complex Industrial Processes  
https://cocop-spire.eu/

This project has received funding from the European Union's Horizon 2020 research and innovation programme under grant agreement No 723661. This piece of software reflects only the authors' views, and the Commission is not responsible for any use that may be made of the information contained therein.

---


Authors
-------

This application is based on the work of Sathish Kumar Narayanan  
https://github.com/ragavsathish/RabbitMQ-Matlab-Client/tree/master/src/mqwrapper

The work of S. K. Narayanan is an implementation of:  
Yair Altman: Matlab callbacks for Java events  
30 Nov 2010  
http://undocumentedmatlab.com/articles/matlab-callbacks-for-java-events

Modified and extended by Petri Kannisto, Tampere University, Finland  
https://github.com/kannisto  
http://kannisto.org

Detailed author information:

* AmqpMathToolConnector
    * Notifier.java
        * original work by S. K. Narayanan
        * modified by P. Kannisto
    * All other classes: P. Kannisto
* AmqpMathToolConnectorTest: P. Kannisto
* AmqpPropsManagerUnitTest: P. Kannisto

**Please make sure to read and understand [LICENSE.txt](./LICENSE.txt)!**


COCOP Toolkit
-------------

This application is a part of COCOP Toolkit, which was developed to enable a decoupled and well-scalable architecture in industrial systems. Please see https://kannisto.github.io/Cocop-Toolkit/


Introduction
------------

The purpose of this application is to connect an AMQP 0-9-1 message bus with a calculation environment. In particular, it was designed to connect Matlab with RabbitMQ.

This repository contains an entire Eclipse workspace. The included applications are as follows:

* AmqpMathToolConnector: the actual connector application
* AmqpMathToolConnectorTest: console application to test connecting with the message bus
* AmqpPropsManagerUnitTest: JUnit unit test for the AmqpPropsManager class

See also:

* Github repo: https://github.com/kannisto/Cocop.AmqpMathToolConnector
* API documentation: https://kannisto.github.io/Cocop.AmqpMathToolConnector


Environment and Libraries
-------------------------

The development environment was _Eclipse Photon Release (4.8.0), Build id: 20180619-1200_.

For execution, the following should do it:

* Java: JDK/JRE 8 or newer
* Matlab: 2017 or newer

The following libraries were utilised in development:

* amqp-client-4.2.2.jar
    * see https://www.rabbitmq.com/download.html
* amqp-client-4.2.2-javadoc.jar
* commons-logging-1.2.jar
* slf4j-api-1.7.25.jar
* slf4j-nop-1.7.25.jar


## Usage in Matlab

To utilise the AMQP connector in Matlab, you can follow these instructions.


### Adding JAR libraries to classpath

Matlab must have an access to the required JAR libraries. Steps:

1. Retrieve the following libraries as JAR files (unclear if the versions can be different):
    * amqp-client-4.2.2.jar
        * see https://www.rabbitmq.com/download.html
    * commons-logging-1.2.jar
    * slf4j-api-1.7.25.jar
    * slf4j-nop-1.7.25.jar
2. Copy the JAR files to whatever folder you want to (such as 'C:\\myclasspath')
3. In your Matlab preferences folder, create a file called 'javaclasspath.txt'
    * to locate this folder, use the 'prefdir' command
        * see https://se.mathworks.com/help/matlab/ref/prefdir.html
    * in the classpath file, add the full path of each JAR file
        * e.g., ```C:\myclasspath\cocop_amqpmathtoolconnector.jar```


### Creating callback for data reception

To receive messages, you must create a callback function in Matlab. For instance:

```
function myAmqpCallback(hObject, eventData)

    routingKey = eventData.routingKey;
    disp('Received a message with routing key:');
    disp(routingKey);
    
    if strcmp(routingKey, 'my.expected.routingkey')
        
        messageAsByteArray = eventData.message;
        % Do whatever you want with the message...
        
    else
        
        disp('Unexpected routing key!');
        return;
        
    end
    
end
```

Steps:

1. Write your callback function in an M file, such as 'C:\\myfunctions\\mycallback.m'
2. Add the folder containing the callback to the Matlab path
    * in your personal MATLAB folder, add a file called 'startup.m'
        * this folder is presumably 'C:\\Users\\(username)\\Documents\\MATLAB'
    * in this file, use addpath to add the folder containing your M file
        * e.g., ```addpath('C:\myfunctions');```


### Activating AMQP listeners

The following code creates an object that will deliver notifications from the specified topic to the callback function "myAmqpCallback".

You can listen to as many topics as needed. However, you must specify all topics as a constructor parameters to AmqpConnector. 

```
% Specifying topics to listen to
topicIn1 = 'topic.in.1';
topicIn2 = 'topic.in.2';

% Specify AMQP properties
amqpProps = eu.cocop.amqp2math.AmqpPropsManager('myhost.com', 'my.exchange', 'user-x', 'my-password');

% If using a non-secure connection:
amqpProps.setSecure(false);
 
% Specify topics to listen to
topicsIn = javaArray('java.lang.String', 2);
topicsIn(1) = java.lang.String(topicIn1);
topicsIn(2) = java.lang.String(topicIn2);
 
% Set up AMQP connector
amqpConnector = eu.cocop.amqp2math.AmqpConnector(amqpProps, topicsIn);
 
% Associate a topic listener with Matlab
notifier = amqpConnector.getNotifierForTopic(topicIn1);
handleObj = handle(notifier, 'CallbackProperties');
set(notifier, 'ListenCallback', @(handleObj, ev)myAmqpCallback(handleObj, ev));
```


### Publishing to AMQP

The following code sends a string.

```
myStringOut = java.lang.String('Hello 5');
myBytesOut = myStringOut.getBytes(java.nio.charset.Charset.forName('UTF-8'));
amqpConnector.sendMessage('my.topic.Out', myBytesOut);
```


### Cleanup

It is important to clean up resources after use. Call this when you end execution:

```
amqpConnector.close();
```
