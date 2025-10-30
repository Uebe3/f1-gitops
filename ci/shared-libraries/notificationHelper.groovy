#!/usr/bin/env groovy

/**
 * Notification Helper Library for F1 Data Platform
 * 
 * This library provides methods for sending notifications to various channels
 * including Slack, Microsoft Teams, email, and other communication platforms.
 */

def sendSlackNotification(Map config) {
    def channel = config.channel ?: env.SLACK_CHANNEL ?: '#f1-deployments'
    def color = config.color ?: 'good'
    def message = config.message ?: 'F1 Data Platform notification'
    def title = config.title ?: 'F1 Data Platform'
    def username = config.username ?: 'F1 Deploy Bot'
    def iconEmoji = config.iconEmoji ?: ':racing_car:'
    def fields = config.fields ?: []
    
    try {
        // Build Slack attachment
        def attachment = [
            color: color,
            title: title,
            text: message,
            footer: "F1 Data Platform CI/CD",
            ts: (System.currentTimeMillis() / 1000).intValue()
        ]
        
        // Add standard fields
        def standardFields = [
            [
                title: "Environment",
                value: env.ENVIRONMENT ?: 'unknown',
                short: true
            ],
            [
                title: "Build",
                value: "#${env.BUILD_NUMBER}",
                short: true
            ],
            [
                title: "Branch",
                value: env.BRANCH_NAME ?: 'unknown',
                short: true
            ],
            [
                title: "Duration",
                value: currentBuild.durationString ?: 'unknown',
                short: true
            ]
        ]
        
        attachment.fields = standardFields + fields
        
        // Add action buttons
        attachment.actions = [
            [
                type: "button",
                text: "View Build",
                url: env.BUILD_URL
            ],
            [
                type: "button",
                text: "Console Log",
                url: "${env.BUILD_URL}console"
            ]
        ]
        
        // Send to Slack
        slackSend(
            channel: channel,
            color: color,
            message: "",
            username: username,
            iconEmoji: iconEmoji,
            attachments: [attachment]
        )
        
        echo "✅ Slack notification sent to ${channel}"
        
    } catch (Exception e) {
        echo "⚠️ Failed to send Slack notification: ${e.getMessage()}"
        // Don't fail the build for notification issues
    }
}

def sendSlackStatusNotification(String status, Map config = [:]) {
    def statusConfig = getStatusConfig(status)
    
    def message = config.message ?: statusConfig.defaultMessage
    def color = config.color ?: statusConfig.color
    def emoji = config.emoji ?: statusConfig.emoji
    
    def enrichedConfig = [
        channel: config.channel,
        color: color,
        message: "${emoji} ${message}",
        title: config.title ?: "F1 Data Platform - ${status}",
        fields: config.fields ?: []
    ]
    
    sendSlackNotification(enrichedConfig)
}

def getStatusConfig(String status) {
    def configs = [
        'STARTED': [
            color: '#36a64f',
            emoji: '🚀',
            defaultMessage: 'Pipeline started'
        ],
        'SUCCESS': [
            color: 'good',
            emoji: '✅',
            defaultMessage: 'Pipeline completed successfully'
        ],
        'FAILURE': [
            color: 'danger',
            emoji: '❌',
            defaultMessage: 'Pipeline failed'
        ],
        'UNSTABLE': [
            color: 'warning',
            emoji: '⚠️',
            defaultMessage: 'Pipeline completed with warnings'
        ],
        'ABORTED': [
            color: '#764FA5',
            emoji: '🛑',
            defaultMessage: 'Pipeline was aborted'
        ],
        'DEPLOYMENT_SUCCESS': [
            color: 'good',
            emoji: '🚀',
            defaultMessage: 'Deployment completed successfully'
        ],
        'DEPLOYMENT_FAILURE': [
            color: 'danger',
            emoji: '💥',
            defaultMessage: 'Deployment failed'
        ],
        'TEST_FAILURE': [
            color: 'warning',
            emoji: '🧪',
            defaultMessage: 'Tests failed'
        ],
        'SECURITY_ALERT': [
            color: 'danger',
            emoji: '🔒',
            defaultMessage: 'Security vulnerability detected'
        ]
    ]
    
    return configs[status] ?: [
        color: 'warning',
        emoji: '❓',
        defaultMessage: "Unknown status: ${status}"
    ]
}

def sendTeamsNotification(Map config) {
    def webhookUrl = config.webhookUrl ?: env.TEAMS_WEBHOOK_URL
    def title = config.title ?: 'F1 Data Platform Notification'
    def message = config.message ?: 'Pipeline notification'
    def color = config.color ?: '0078D4'
    def facts = config.facts ?: []
    
    if (!webhookUrl) {
        echo "⚠️ Teams webhook URL not configured, skipping Teams notification"
        return
    }
    
    try {
        // Build Teams adaptive card
        def card = [
            "@type": "MessageCard",
            "@context": "https://schema.org/extensions",
            summary: title,
            themeColor: color,
            sections: [
                [
                    activityTitle: title,
                    activitySubtitle: message,
                    activityImage: "https://raw.githubusercontent.com/jenkins-x/jenkins-x-website/master/images/logo/jenkinsx-icon-color.svg",
                    facts: [
                        [name: "Environment", value: env.ENVIRONMENT ?: 'unknown'],
                        [name: "Build", value: "#${env.BUILD_NUMBER}"],
                        [name: "Branch", value: env.BRANCH_NAME ?: 'unknown'],
                        [name: "Duration", value: currentBuild.durationString ?: 'unknown']
                    ] + facts
                ]
            ],
            potentialAction: [
                [
                    "@type": "OpenUri",
                    name: "View Build",
                    targets: [
                        [os: "default", uri: env.BUILD_URL]
                    ]
                ],
                [
                    "@type": "OpenUri",
                    name: "Console Log",
                    targets: [
                        [os: "default", uri: "${env.BUILD_URL}console"]
                    ]
                ]
            ]
        ]
        
        // Send HTTP request to Teams webhook
        def response = httpRequest(
            httpMode: 'POST',
            url: webhookUrl,
            contentType: 'APPLICATION_JSON',
            requestBody: groovy.json.JsonOutput.toJson(card)
        )
        
        if (response.status == 200) {
            echo "✅ Teams notification sent successfully"
        } else {
            echo "⚠️ Teams notification failed with status: ${response.status}"
        }
        
    } catch (Exception e) {
        echo "⚠️ Failed to send Teams notification: ${e.getMessage()}"
    }
}

def sendEmailNotification(Map config) {
    def recipients = config.recipients ?: env.EMAIL_RECIPIENTS
    def subject = config.subject ?: "F1 Data Platform - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    def body = config.body ?: generateEmailBody(config)
    def attachments = config.attachments ?: ''
    def mimeType = config.mimeType ?: 'text/html'
    
    if (!recipients) {
        echo "⚠️ No email recipients configured, skipping email notification"
        return
    }
    
    try {
        emailext(
            to: recipients,
            subject: subject,
            body: body,
            attachmentsPattern: attachments,
            mimeType: mimeType
        )
        
        echo "✅ Email notification sent to: ${recipients}"
        
    } catch (Exception e) {
        echo "⚠️ Failed to send email notification: ${e.getMessage()}"
    }
}

def generateEmailBody(Map config) {
    def status = currentBuild.currentResult ?: 'UNKNOWN'
    def statusColor = getEmailStatusColor(status)
    def emoji = getStatusConfig(status).emoji
    
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <style>
            body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }
            .container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
            .header { background-color: ${statusColor}; color: white; padding: 20px; text-align: center; }
            .content { padding: 20px; }
            .footer { background-color: #f8f9fa; padding: 15px; text-align: center; font-size: 12px; color: #666; }
            .info-table { width: 100%; border-collapse: collapse; margin: 20px 0; }
            .info-table th, .info-table td { padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }
            .info-table th { background-color: #f8f9fa; font-weight: bold; }
            .button { display: inline-block; padding: 10px 20px; background-color: #007bff; color: white; text-decoration: none; border-radius: 5px; margin: 5px; }
            .button:hover { background-color: #0056b3; }
        </style>
    </head>
    <body>
        <div class="container">
            <div class="header">
                <h1>${emoji} F1 Data Platform</h1>
                <h2>Build ${status}</h2>
            </div>
            <div class="content">
                <p>The F1 Data Platform pipeline has completed with status: <strong>${status}</strong></p>
                
                <table class="info-table">
                    <tr><th>Job</th><td>${env.JOB_NAME}</td></tr>
                    <tr><th>Build Number</th><td>#${env.BUILD_NUMBER}</td></tr>
                    <tr><th>Branch</th><td>${env.BRANCH_NAME ?: 'unknown'}</td></tr>
                    <tr><th>Environment</th><td>${env.ENVIRONMENT ?: 'unknown'}</td></tr>
                    <tr><th>Duration</th><td>${currentBuild.durationString ?: 'unknown'}</td></tr>
                    <tr><th>Started By</th><td>${currentBuild.getBuildCauses('hudson.model.Cause\$UserIdCause')?.userId?.join(', ') ?: 'System'}</td></tr>
                </table>
                
                ${config.additionalContent ?: ''}
                
                <div style="text-align: center; margin: 20px 0;">
                    <a href="${env.BUILD_URL}" class="button">View Build Details</a>
                    <a href="${env.BUILD_URL}console" class="button">View Console Log</a>
                </div>
            </div>
            <div class="footer">
                <p>F1 Data Platform CI/CD System</p>
                <p>Generated at: ${new Date().format('yyyy-MM-dd HH:mm:ss')}</p>
            </div>
        </div>
    </body>
    </html>
    """
}

def getEmailStatusColor(String status) {
    def colors = [
        'SUCCESS': '#28a745',
        'FAILURE': '#dc3545',
        'UNSTABLE': '#ffc107',
        'ABORTED': '#6c757d'
    ]
    
    return colors[status] ?: '#17a2b8'
}

def sendWebhookNotification(Map config) {
    def webhookUrl = config.webhookUrl
    def payload = config.payload ?: [:]
    def headers = config.headers ?: [:]
    def method = config.method ?: 'POST'
    
    if (!webhookUrl) {
        echo "⚠️ Webhook URL not provided, skipping webhook notification"
        return
    }
    
    try {
        // Build default payload if not provided
        if (!payload) {
            payload = [
                event: 'pipeline_notification',
                timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'"),
                build: [
                    number: env.BUILD_NUMBER,
                    job: env.JOB_NAME,
                    branch: env.BRANCH_NAME,
                    status: currentBuild.currentResult,
                    url: env.BUILD_URL,
                    duration: currentBuild.duration
                ],
                environment: env.ENVIRONMENT,
                version: env.BUILD_VERSION
            ]
        }
        
        def response = httpRequest(
            httpMode: method,
            url: webhookUrl,
            contentType: 'APPLICATION_JSON',
            customHeaders: headers.collect { key, value -> [name: key, value: value] },
            requestBody: groovy.json.JsonOutput.toJson(payload)
        )
        
        if (response.status >= 200 && response.status < 300) {
            echo "✅ Webhook notification sent successfully to: ${webhookUrl}"
        } else {
            echo "⚠️ Webhook notification failed with status: ${response.status}"
        }
        
    } catch (Exception e) {
        echo "⚠️ Failed to send webhook notification: ${e.getMessage()}"
    }
}

def sendPagerDutyAlert(Map config) {
    def integrationKey = config.integrationKey ?: env.PAGERDUTY_INTEGRATION_KEY
    def eventAction = config.eventAction ?: 'trigger'  // trigger, acknowledge, resolve
    def severity = config.severity ?: 'error'
    def summary = config.summary ?: "F1 Data Platform Alert"
    def source = config.source ?: env.JOB_NAME
    def component = config.component ?: 'F1 Data Platform'
    def group = config.group ?: 'CI/CD'
    def details = config.details ?: [:]
    
    if (!integrationKey) {
        echo "⚠️ PagerDuty integration key not configured, skipping PagerDuty alert"
        return
    }
    
    try {
        def payload = [
            routing_key: integrationKey,
            event_action: eventAction,
            payload: [
                summary: summary,
                source: source,
                severity: severity,
                component: component,
                group: group,
                custom_details: [
                    build_number: env.BUILD_NUMBER,
                    build_url: env.BUILD_URL,
                    job_name: env.JOB_NAME,
                    branch: env.BRANCH_NAME,
                    environment: env.ENVIRONMENT
                ] + details
            ]
        ]
        
        def response = httpRequest(
            httpMode: 'POST',
            url: 'https://events.pagerduty.com/v2/enqueue',
            contentType: 'APPLICATION_JSON',
            requestBody: groovy.json.JsonOutput.toJson(payload)
        )
        
        if (response.status == 202) {
            echo "✅ PagerDuty alert sent successfully"
        } else {
            echo "⚠️ PagerDuty alert failed with status: ${response.status}"
        }
        
    } catch (Exception e) {
        echo "⚠️ Failed to send PagerDuty alert: ${e.getMessage()}"
    }
}

def notifyBuildStart(Map config = [:]) {
    echo "📢 Sending build start notifications..."
    
    sendSlackStatusNotification('STARTED', [
        message: config.message ?: "Started deployment to ${env.ENVIRONMENT ?: 'unknown'}",
        channel: config.slackChannel,
        fields: [
            [
                title: "Git Commit",
                value: env.GIT_COMMIT?.take(8) ?: 'unknown',
                short: true
            ]
        ]
    ])
    
    if (config.enableTeams) {
        sendTeamsNotification([
            title: "🚀 F1 Pipeline Started",
            message: config.message ?: "Pipeline started for ${env.ENVIRONMENT ?: 'unknown'}",
            color: "0078D4"
        ])
    }
}

def notifyBuildSuccess(Map config = [:]) {
    echo "📢 Sending build success notifications..."
    
    sendSlackStatusNotification('SUCCESS', [
        message: config.message ?: "Successfully deployed to ${env.ENVIRONMENT ?: 'unknown'}",
        channel: config.slackChannel,
        fields: config.additionalFields ?: []
    ])
    
    if (config.enableTeams) {
        sendTeamsNotification([
            title: "✅ F1 Pipeline Success",
            message: config.message ?: "Pipeline completed successfully",
            color: "00C851"
        ])
    }
    
    if (config.enableEmail) {
        sendEmailNotification([
            recipients: config.emailRecipients,
            subject: "✅ F1 Data Platform Deployment Success - ${env.ENVIRONMENT}",
            additionalContent: config.emailContent
        ])
    }
}

def notifyBuildFailure(Map config = [:]) {
    echo "📢 Sending build failure notifications..."
    
    sendSlackStatusNotification('FAILURE', [
        message: config.message ?: "Deployment failed in ${env.ENVIRONMENT ?: 'unknown'}",
        channel: config.slackChannel,
        fields: [
            [
                title: "Failed Stage",
                value: env.STAGE_NAME ?: 'unknown',
                short: true
            ],
            [
                title: "Error",
                value: config.error ?: 'See build logs for details',
                short: false
            ]
        ]
    ])
    
    if (config.enableTeams) {
        sendTeamsNotification([
            title: "❌ F1 Pipeline Failed",
            message: config.message ?: "Pipeline failed - immediate attention required",
            color: "FF4444"
        ])
    }
    
    if (config.enableEmail) {
        sendEmailNotification([
            recipients: config.emailRecipients,
            subject: "❌ F1 Data Platform Deployment Failed - ${env.ENVIRONMENT}",
            additionalContent: config.emailContent
        ])
    }
    
    // Send PagerDuty alert for production failures
    if (env.ENVIRONMENT == 'prod' && config.enablePagerDuty) {
        sendPagerDutyAlert([
            severity: 'error',
            summary: "F1 Data Platform production deployment failed",
            details: [
                stage: env.STAGE_NAME,
                error: config.error
            ]
        ])
    }
}

def notifySecurityAlert(Map config) {
    echo "🔒 Sending security alert notifications..."
    
    sendSlackStatusNotification('SECURITY_ALERT', [
        message: config.message ?: "Security vulnerability detected in pipeline",
        channel: config.securityChannel ?: '#security-alerts',
        fields: [
            [
                title: "Severity",
                value: config.severity ?: 'Unknown',
                short: true
            ],
            [
                title: "Component",
                value: config.component ?: 'Unknown',
                short: true
            ]
        ]
    ])
    
    // Always send email for security alerts
    sendEmailNotification([
        recipients: config.securityTeam ?: env.SECURITY_EMAIL_RECIPIENTS,
        subject: "🔒 SECURITY ALERT - F1 Data Platform",
        additionalContent: """
        <div style="background-color: #f8d7da; border: 1px solid #f5c6cb; color: #721c24; padding: 15px; border-radius: 5px; margin: 20px 0;">
            <h3>🔒 Security Alert Details</h3>
            <p><strong>Severity:</strong> ${config.severity ?: 'Unknown'}</p>
            <p><strong>Component:</strong> ${config.component ?: 'Unknown'}</p>
            <p><strong>Description:</strong> ${config.description ?: 'See build logs for details'}</p>
        </div>
        """
    ])
}

def createNotificationSummary(Map results) {
    echo "📋 Creating notification summary..."
    
    def summary = [
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
        build: env.BUILD_NUMBER,
        job: env.JOB_NAME,
        environment: env.ENVIRONMENT,
        results: results,
        metrics: [
            total_notifications: results.size(),
            successful: results.count { it.value.status == 'success' },
            failed: results.count { it.value.status == 'failed' }
        ]
    ]
    
    writeJSON file: 'notification-summary.json', json: summary
    archiveArtifacts artifacts: 'notification-summary.json', allowEmptyArchive: true
    
    return summary
}

// Export functions for Jenkins pipeline use
return this