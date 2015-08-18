# PluginScrubber
Scrubs malicious code from plugins

## Why? 
There have been reports of malicious code being injected into plugins without author knowledge.
This executable removes the infection from plugin jars.
        
## How?
1. Download Scrubber.jar from the [latest release](https://github.com/PluginScrubber/PluginScrubber/releases/latest).
2. Place Scrubber.jar in your server folder.
3. Run Scrubber like so: `java -jar Scrubber.jar`
  * If on a host with a panel and FTP, upload it like a custom server jar and have the panel run it like one.

## Safe?
* The source of Scrubber is available for viewing.
* The executable is automatically created by [Travis CI](https://travis-ci.org/PluginScrubber/PluginScrubber) and published to release link above.
  * Feel free to build it yourself instead.
