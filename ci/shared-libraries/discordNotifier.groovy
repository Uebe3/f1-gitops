#!/usr/bin/env groovy

/**
 * Discord Notification Helper for Jenkins Pipelines
 * Replaces Slack with Discord webhooks
 */

class DiscordNotifier implements Serializable {
    def script
    def webhookUrl
    
    DiscordNotifier(script, webhookUrl) {
        this.script = script
        this.webhookUrl = webhookUrl
    }
    
    /**
     * Send notification to Discord
     */
    def notify(Map config) {
        def status = config.status ?: 'UNKNOWN'
        def message = config.message ?: 'Pipeline notification'
        def color = getColorForStatus(status)
        def emoji = getEmojiForStatus(status)
        
        def payload = [
            content: "${emoji} **${message}**",
            embeds: [[
                title: config.title ?: "F1 Data Platform Pipeline",
                description: config.description ?: "",
                color: color,
                fields: buildFields(config),
                timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone('UTC')),
                footer: [
                    text: "Jenkins CI/CD",
                    icon_url: "https://www.jenkins.io/images/logos/jenkins/jenkins.png"
                ]
            ]]
        ]
        
        sendWebhook(payload)
    }
    
    /**
     * Send deployment started notification
     */
    def notifyDeploymentStarted(Map config) {
        notify([
            status: 'STARTED',
            title: "🚀 Deployment Started",
            description: "Deploying to ${config.environment ?: 'unknown'} environment",
            fields: [
                [name: "Environment", value: config.environment ?: 'unknown', inline: true],
                [name: "Branch", value: config.branch ?: 'unknown', inline: true],
                [name: "Commit", value: config.commit?.take(8) ?: 'unknown', inline: true],
                [name: "Triggered By", value: config.triggeredBy ?: 'Jenkins', inline: true]
            ]
        ])
    }
    
    /**
     * Send deployment success notification
     */
    def notifyDeploymentSuccess(Map config) {
        notify([
            status: 'SUCCESS',
            title: "✅ Deployment Successful",
            description: "Successfully deployed to ${config.environment ?: 'unknown'}",
            fields: [
                [name: "Environment", value: config.environment ?: 'unknown', inline: true],
                [name: "Duration", value: config.duration ?: 'unknown', inline: true],
                [name: "CloudFormation Stacks", value: config.stacks?.toString() ?: 'N/A', inline: false],
                [name: "Resources Created", value: config.resources?.toString() ?: 'N/A', inline: false]
            ]
        ])
    }
    
    /**
     * Send deployment failure notification
     */
    def notifyDeploymentFailure(Map config) {
        notify([
            status: 'FAILURE',
            title: "❌ Deployment Failed",
            description: "Deployment to ${config.environment ?: 'unknown'} failed",
            fields: [
                [name: "Environment", value: config.environment ?: 'unknown', inline: true],
                [name: "Stage", value: config.failedStage ?: 'unknown', inline: true],
                [name: "Error", value: config.error?.take(200) ?: 'See Jenkins logs', inline: false],
                [name: "Build URL", value: config.buildUrl ?: 'N/A', inline: false]
            ]
        ])
    }
    
    /**
     * Send quality gate notification
     */
    def notifyQualityGate(Map config) {
        def passed = config.passed ?: false
        notify([
            status: passed ? 'SUCCESS' : 'FAILURE',
            title: passed ? "✅ Quality Gate Passed" : "❌ Quality Gate Failed",
            description: "SonarQube analysis complete",
            fields: [
                [name: "Quality Gate", value: passed ? 'PASSED' : 'FAILED', inline: true],
                [name: "Coverage", value: "${config.coverage ?: 0}%", inline: true],
                [name: "Bugs", value: config.bugs?.toString() ?: '0', inline: true],
                [name: "Code Smells", value: config.codeSmells?.toString() ?: '0', inline: true],
                [name: "Security Hotspots", value: config.securityHotspots?.toString() ?: '0', inline: true],
                [name: "SonarQube URL", value: config.sonarUrl ?: 'N/A', inline: false]
            ]
        ])
    }
    
    /**
     * Send test results notification
     */
    def notifyTestResults(Map config) {
        def total = config.total ?: 0
        def passed = config.passed ?: 0
        def failed = config.failed ?: 0
        def skipped = config.skipped ?: 0
        def allPassed = (failed == 0)
        
        notify([
            status: allPassed ? 'SUCCESS' : 'UNSTABLE',
            title: allPassed ? "✅ All Tests Passed" : "⚠️ Some Tests Failed",
            description: "Test execution complete",
            fields: [
                [name: "Total Tests", value: total.toString(), inline: true],
                [name: "Passed", value: passed.toString(), inline: true],
                [name: "Failed", value: failed.toString(), inline: true],
                [name: "Skipped", value: skipped.toString(), inline: true],
                [name: "Success Rate", value: "${total > 0 ? ((passed/total)*100).round(2) : 0}%", inline: true]
            ]
        ])
    }
    
    /**
     * Get color for status
     */
    private int getColorForStatus(String status) {
        switch(status.toUpperCase()) {
            case 'SUCCESS':
                return 65280  // Green
            case 'FAILURE':
                return 16711680  // Red
            case 'UNSTABLE':
                return 16776960  // Yellow
            case 'ABORTED':
                return 8421504  // Gray
            case 'STARTED':
                return 3447003  // Blue
            default:
                return 9807270  // Light Gray
        }
    }
    
    /**
     * Get emoji for status
     */
    private String getEmojiForStatus(String status) {
        switch(status.toUpperCase()) {
            case 'SUCCESS':
                return '✅'
            case 'FAILURE':
                return '❌'
            case 'UNSTABLE':
                return '⚠️'
            case 'ABORTED':
                return '🛑'
            case 'STARTED':
                return '🚀'
            default:
                return 'ℹ️'
        }
    }
    
    /**
     * Build fields array from config
     */
    private List buildFields(Map config) {
        def fields = config.fields ?: []
        
        // Add default fields if not provided
        if (!fields && config.environment) {
            fields << [name: "Environment", value: config.environment, inline: true]
        }
        if (!fields && config.branch) {
            fields << [name: "Branch", value: config.branch, inline: true]
        }
        
        return fields
    }
    
    /**
     * Send webhook HTTP request
     */
    private void sendWebhook(Map payload) {
        try {
            def jsonPayload = script.groovy.json.JsonOutput.toJson(payload)
            
            script.sh """
                curl -X POST '${webhookUrl}' \\
                     -H 'Content-Type: application/json' \\
                     -d '${jsonPayload}'
            """
        } catch (Exception e) {
            script.echo "Failed to send Discord notification: ${e.message}"
            // Don't fail the build if notification fails
        }
    }
}

// Export for use in pipelines
return DiscordNotifier
