/**
 *  Tesla
 *
 *  Copyright 2018 Trent Foley
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * lgk kahn@lgk.com 10/13/20 Added user selectable refresh update schedule instead of default 15 minutes.
 * also add custom lastupdate time attribute and refreshTime so that info is displayable on dashboards.
 * also round mileage off to whole number so again it appears better on dashboard.
 * same for temp, round off so that we can do custom colors on dashbaord based on temp.
 * Same for temp setpoint. showing non integer makes no sens.
 * 10/18/20 added unlock/open charge port command
 * 12/25/20 new attributes and functions for seat heater,windows etc thanks to gomce62f, Also add input to set temp scale to either F or C.
 
 * lgk new versino, not letting the car sleep, add option to disable and schedule it between certain times.
 * lgk add misssing parameter to sethermostatsetpoint.. note it is in farenheit so be aware of that.. in the future i can look at supporting both
 * with option to convert.
 * lgk add code to turn off debugging after 30 minutes.
*/
metadata {
	definition (name: "Tesla", namespace: "trentfoley", author: "Trent Foley, Larry Kahn") {
		capability "Actuator"
		capability "Battery"
// not supported	capability "Geolocation"
		capability "Lock"
		capability "Motion Sensor"
		capability "Presence Sensor"
		capability "Refresh"
		capability "Temperature Measurement"
		capability "Thermostat Mode"
        capability "Thermostat Setpoint"
     
		attribute "state", "string"
        attribute "vin", "string"
        attribute "odometer", "number"
        attribute "batteryRange", "number"
        attribute "chargingState", "string"
        attribute "refreshTime", "string"
        attribute "lastUpdate", "string"
        attribute "minutes_to_full_charge", "number"
        attribute "seat_heater_left", "number"
        attribute "seat_heater_right", "number"        
        attribute "seat_heater_rear_left", "number"
        attribute "seat_heater_rear_right", "number"
        attribute "seat_heater_rear_center", "number"    
        attribute "sentry_mode", "string"
        attribute "front_drivers_window" , "number"
        attribute "front_pass_window" , "number"
        attribute "rear_drivers_window" , "number"
        attribute "rear_pass_window" , "number"

		command "wake"
        command "setThermostatSetpoint", ["Number"]
        command "startCharge"
        command "stopCharge"
        command "openFrontTrunk"
        command "openRearTrunk"
        command "unlockandOpenChargePort"
        command "setSeatHeaters", ["number","number"]  /** first attribute is seat number 0-5 and second attribute is heat level 0-3 e.g. 0,3 is drivers seat heat to max *  Future plan is to have this be  drop down list */ 
        command "sentryModeOn"
        command "sentryModeOff"
        command "ventWindows"
        command "closeWindows"

	}


	simulator {
		// TODO: define status and reply messages here
	}


    preferences
    {
       input "refreshTime", "enum", title: "How often to refresh?",options: ["Disabled","1-Hour", "30-Minutes", "15-Minutes", "10-Minutes", "5-Minutes"],  required: true, defaultValue: "15-Minutes"
       input "AllowSleep", "bool", title: "Schedule a time to disable/reenable to allow the car to sleep?", required: true, defaultValue: false
       input "fromTime", "time", title: "From", required:false, width: 6, submitOnChange:true
       input "toTime", "time", title: "To", required:false, width: 6 
       input "tempScale", "enum", title: "Display temperature in F or C ?", options: ["F", "C"], required: true, defaultValue: "F" 
       input "debug", "bool", title: "Turn on Debug Logging?", required:true, defaultValue: false   
    }
}

def logsOff()
{
    log.debug "Turning off Logging!"
    device.updateSetting("debug",[value:"false",type:"bool"])
}

def initialize() {
	log.debug "Executing 'initialize'"
     def now = new Date().format('MM/dd/yyyy h:mm a',location.timeZone)
    
    sendEvent(name: "supportedThermostatModes", value: ["auto", "off"])
    log.debug "Refresh time currently set to: $refreshTime"
    unschedule()  
   
    sendEvent(name: "lastUpdate", value: now, descriptionText: "Last Update: $now")
    sendEvent(name: "refreshTime", value: refreshTime)
    
    if (refreshTime == "1-Hour")
      runEvery1Hour(refresh)
      else if (refreshTime == "30-Minutes")
       runEvery30Minutes(refresh)
     else if (refreshTime == "15-Minutes")
       runEvery15Minutes(refresh)
     else if (refreshTime == "10-Minutes")
       runEvery10Minutes(refresh)
     else if (refreshTime == "5-Minutes")
       runEvery5Minutes(refresh)
    else if (refreshTime == "Disabled")
    {
        log.debug "Disabling..."
    }
      else 
      { 
          log.debug "Unknown refresh time specified.. defaulting to 15 Minutes"
          runEvery15Minutes(refresh)
      }
    // now handle scheduling to turn on and off to allow sleep
    if ((AllowSleep == true) && (fromTime != null) && (toTime != null))
    {
       log.debug "Scheduling disable and re-enable times to allow sleep!" 
        schedule(fromTime, disable)
        schedule(toTime, reenable)       
    }
    
     if (debug)
    {
        log.debug "Turning off logging in 1/2 hour!"
        runIn(1800,logsOff)
    } 
   
}


def disable()
{
    log.debug "Disabling to allow sleep!"
    unschedule()
    // schedule reenable time
    if (toTime != null)
     schedule(toTime, reenable)
}

def reenable()
{
    log.debug "Waking up app in re-enable!"
    // now schedule the sleep again
    initialize() 
    wake()
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}
    
private processData(data) {
	if(data) {
    	log.debug "processData: ${data}"
        
    	sendEvent(name: "state", value: data.state)
        sendEvent(name: "motion", value: data.motion)
        sendEvent(name: "speed", value: data.speed, unit: "mph")
        sendEvent(name: "vin", value: data.vin)
        sendEvent(name: "thermostatMode", value: data.thermostatMode)
        
        if (data.chargeState) {
            if (debug) log.debug "chargeStte = $data.chargeState"
            
        	sendEvent(name: "battery", value: data.chargeState.battery)
            sendEvent(name: "batteryRange", value: data.chargeState.batteryRange.toInteger())
            sendEvent(name: "chargingState", value: data.chargeState.chargingState)
            sendEvent(name: "minutes_to_full_charge", value: data.chargeState.minutes_to_full_charge)

        }
        
        if (data.driveState) {
        	sendEvent(name: "latitude", value: data.driveState.latitude)
			sendEvent(name: "longitude", value: data.driveState.longitude)
            sendEvent(name: "method", value: data.driveState.method)
            sendEvent(name: "heading", value: data.driveState.heading)
            sendEvent(name: "lastUpdateTime", value: data.driveState.lastUpdateTime)
        }
        
        if (data.vehicleState) {
           if (debug) log.debug "vehicle state = $data.vehicleState"
            
        	sendEvent(name: "presence", value: data.vehicleState.presence)
            sendEvent(name: "lock", value: data.vehicleState.lock)
            sendEvent(name: "odometer", value: data.vehicleState.odometer.toInteger())
            sendEvent(name: "sentry_mode", value: data.vehicleState.sentry_mode)
            sendEvent(name: "front_drivers_window" , value: data.vehicleState.front_drivers_window)
            sendEvent(name: "front_pass_window" , value: data.vehicleState.front_pass_window)
            sendEvent(name: "rear_drivers_window" , value: data.vehicleState.rear_drivers_window)
            sendEvent(name: "rear_pass_window" , value: data.vehicleState.rear_pass_window)

        }
        
        if (data.climateState) {
            if (tempScale == "F")
            {
        	  sendEvent(name: "temperature", value: data.climateState.temperature.toInteger(), unit: "F")
              sendEvent(name: "thermostatSetpoint", value: data.climateState.thermostatSetpoint.toInteger(), unit: "F")
            }
            else
            {
              sendEvent(name: "temperature", value: farenhietToCelcius(data.climateState.temperature).toInteger(), unit: "C")
              sendEvent(name: "thermostatSetpoint", value: farenhietToCelcius(data.climateState.thermostatSetpoint).toInteger(), unit: "C")
            }
            
            sendEvent(name: "seat_heater_left", value: data.climateState.seat_heater_left)
            sendEvent(name: "seat_heater_right", value: data.climateState.seat_heater_right)            
            sendEvent(name: "seat_heater_rear_left", value: data.climateState.seat_heater_rear_left) 
            sendEvent(name: "seat_heater_rear_right", value: data.climateState.seat_heater_rear_right)
            sendEvent(name: "seat_heater_rear_center", value: data.climateState.seat_heater_rear_center)

        }
	} else {
    	log.error "No data found for ${device.deviceNetworkId}"
    }
}

def refresh() {
	log.debug "Executing 'refresh'"
     def now = new Date().format('MM/dd/yyyy h:mm a',location.timeZone)
     sendEvent(name: "lastUpdate", value: now, descriptionText: "Last Update: $now")
   
    def data = parent.refresh(this)
	processData(data)
}

def wake() {
	log.debug "Executing 'wake'"
	def data = parent.wake(this)
    processData(data)
    runIn(30, refresh)
}

def lock() {
	log.debug "Executing 'lock'"
	def result = parent.lock(this)
    if (result) { refresh() }
}

def unlock() {
	log.debug "Executing 'unlock'"
	def result = parent.unlock(this)
    if (result) { refresh() }
}

def auto() {
	log.debug "Executing 'auto'"
	def result = parent.climateAuto(this)
    if (result) { refresh() }
}

def off() {
	log.debug "Executing 'off'"
	def result = parent.climateOff(this)
    if (result) { refresh() }
}

def heat() {
	log.debug "Executing 'heat'"
	// Not supported
}

def emergencyHeat() {
	log.debug "Executing 'emergencyHeat'"
	// Not supported
}

def cool() {
	log.debug "Executing 'cool'"
	// Not supported
}

def setThermostatMode(mode) {
	log.debug "Executing 'setThermostatMode'"
	switch (mode) {
    	case "auto":
        	auto()
            break
        case "off":
        	off()
            break
        default:
        	log.error "setThermostatMode: Only thermostat modes Auto and Off are supported"
    }
}

def setThermostatSetpoint(Number setpoint) {
	log.debug "Executing 'setThermostatSetpoint with temp scale $tempScale'"
    if (tempScale == "F")
      {
	    def result = parent.setThermostatSetpointF(this, setpoint)
        if (result) { refresh() }
      }
    else
    {
        def result = parent.setThermostatSetpointC(this, setpoint)
        if (result) { refresh() }
    }
}

def startCharge() {
	log.debug "Executing 'startCharge'"
    def result = parent.startCharge(this)
    if (result) { refresh() }
}

def stopCharge() {
	log.debug "Executing 'stopCharge'"
    def result = parent.stopCharge(this)
    if (result) { refresh() }
}

def openFrontTrunk() {
	log.debug "Executing 'openFrontTrunk'"
    def result = parent.openTrunk(this, "front")
    // if (result) { refresh() }
}

def openRearTrunk() {
	log.debug "Executing 'openRearTrunk'"
    def result = parent.openTrunk(this, "rear")
    // if (result) { refresh() }
}

def unlockandOpenChargePort() {
	log.debug "Executing 'unock and open charge port'"
    def result = parent.unlockandOpenChargePort(this)
    // if (result) { refresh() }   
}  

def updated()
{
   
   initialize()
    
}

def setSeatHeaters(seat,level) {
	log.debug "Executing 'setSeatheater'"
	def result = parent.setSeatHeaters(this, seat,level)
    if (result) { refresh() }
}

def sentryModeOn() {
	log.debug "Executing 'Turn Sentry Mode On'"
	def result = parent.sentryModeOn(this)
    if (result) { refresh() }
}

def sentryModeOff() {
	log.debug "Executing 'Turn Sentry Mode Off'"
	def result = parent.sentryModeOff(this)
    if (result) { refresh() }
}

def ventWindows() {
	log.debug "Executing 'Venting Windows'"
	def result = parent.ventWindows(this)
    if (result) { refresh() }
}

def closeWindows() {
	log.debug "Executing 'Close Windows'"
	def result = parent.closeWindows(this)
    if (result) { refresh() }
}


private farenhietToCelcius(dF) {
	return (dF - 32) * 5/9
}
