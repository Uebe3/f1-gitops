#!/usr/bin/env groovy

/**
 * Test Runner Library for F1 Data Platform
 * 
 * This library provides methods for running different types of tests
 * including unit tests, integration tests, health checks, and performance tests.
 */

def runHealthChecks(Map config) {
    echo "🏥 Running health checks..."
    
    def clouds = config.clouds ?: ['aws']
    def environment = config.environment ?: 'dev'
    def timeout = config.timeout ?: 300 // 5 minutes
    
    def healthResults = [:]
    
    clouds.each { cloud ->
        echo "🔍 Running health checks for ${cloud}..."
        healthResults[cloud] = runCloudHealthCheck(cloud, environment, timeout)
    }
    
    // Generate health check report
    generateHealthReport(healthResults, environment)
    
    // Fail if any critical health checks failed
    def criticalFailures = healthResults.findAll { cloud, result -> 
        result.status == 'CRITICAL_FAILURE' 
    }
    
    if (criticalFailures) {
        error "Critical health check failures detected: ${criticalFailures.keySet().join(', ')}"
    }
    
    echo "✅ Health checks completed"
    return healthResults
}

def runCloudHealthCheck(String cloud, String environment, int timeout) {
    def startTime = System.currentTimeMillis()
    def result = [status: 'UNKNOWN', checks: [:], duration: 0]
    
    try {
        switch(cloud) {
            case 'aws':
                result = runAWSHealthChecks(environment, timeout)
                break
            case 'azure':
                result = runAzureHealthChecks(environment, timeout)
                break
            case 'gcp':
                result = runGCPHealthChecks(environment, timeout)
                break
            default:
                result.status = 'ERROR'
                result.message = "Unknown cloud provider: ${cloud}"
        }
    } catch (Exception e) {
        result.status = 'ERROR'
        result.message = e.getMessage()
    } finally {
        result.duration = System.currentTimeMillis() - startTime
    }
    
    return result
}

def runAWSHealthChecks(String environment, int timeout) {
    echo "☁️ Running AWS health checks for ${environment}..."
    
    def result = [status: 'HEALTHY', checks: [:], message: '']
    def checks = [:]
    
    try {
        // Check AWS credentials and connectivity
        checks['aws_connectivity'] = checkAWSConnectivity()
        
        // Check S3 data lake bucket
        checks['s3_data_lake'] = checkS3DataLake(environment)
        
        // Check Glue resources
        checks['glue_resources'] = checkGlueResources(environment)
        
        // Check Athena workgroup
        checks['athena_workgroup'] = checkAthenaWorkgroup(environment)
        
        // Check Lambda functions
        checks['lambda_functions'] = checkLambdaFunctions(environment)
        
        // Check CloudWatch monitoring
        checks['cloudwatch_monitoring'] = checkCloudWatchMonitoring(environment)
        
        result.checks = checks
        
        // Determine overall status
        def failedChecks = checks.findAll { name, status -> status != 'PASS' }
        if (failedChecks) {
            def criticalFailures = failedChecks.findAll { name, status -> status == 'CRITICAL' }
            result.status = criticalFailures ? 'CRITICAL_FAILURE' : 'WARNING'
            result.message = "Failed checks: ${failedChecks.keySet().join(', ')}"
        }
        
    } catch (Exception e) {
        result.status = 'ERROR'
        result.message = e.getMessage()
    }
    
    return result
}

def checkAWSConnectivity() {
    try {
        def output = sh(script: 'aws sts get-caller-identity', returnStdout: true).trim()
        def identity = readJSON text: output
        echo "✅ AWS connectivity verified - Account: ${identity.Account}"
        return 'PASS'
    } catch (Exception e) {
        echo "❌ AWS connectivity failed: ${e.getMessage()}"
        return 'CRITICAL'
    }
}

def checkS3DataLake(String environment) {
    try {
        def bucketName = "f1-data-lake-${environment}-\${AWS_ACCOUNT_ID}"
        
        // Check if bucket exists
        sh "aws s3 ls s3://${bucketName}/"
        
        // Check bucket permissions
        sh "aws s3api head-bucket --bucket ${bucketName}"
        
        echo "✅ S3 data lake bucket is accessible"
        return 'PASS'
    } catch (Exception e) {
        echo "❌ S3 data lake check failed: ${e.getMessage()}"
        return 'CRITICAL'
    }
}

def checkGlueResources(String environment) {
    try {
        def databaseName = "f1_data_${environment}"
        def crawlerName = "f1-data-crawler-${environment}"
        
        // Check Glue database
        sh "aws glue get-database --name ${databaseName}"
        
        // Check Glue crawler
        def crawlerStatus = sh(
            script: "aws glue get-crawler --name ${crawlerName} --query 'Crawler.State' --output text",
            returnStdout: true
        ).trim()
        
        echo "✅ Glue resources are available - Crawler state: ${crawlerStatus}"
        return 'PASS'
    } catch (Exception e) {
        echo "⚠️ Glue resources check failed: ${e.getMessage()}"
        return 'WARNING'
    }
}

def checkAthenaWorkgroup(String environment) {
    try {
        def workgroupName = "f1-data-platform-${environment}-analytics"
        
        def workgroupState = sh(
            script: "aws athena get-work-group --work-group ${workgroupName} --query 'WorkGroup.State' --output text",
            returnStdout: true
        ).trim()
        
        if (workgroupState == 'ENABLED') {
            echo "✅ Athena workgroup is enabled"
            return 'PASS'
        } else {
            echo "⚠️ Athena workgroup is not enabled: ${workgroupState}"
            return 'WARNING'
        }
    } catch (Exception e) {
        echo "⚠️ Athena workgroup check failed: ${e.getMessage()}"
        return 'WARNING'
    }
}

def checkLambdaFunctions(String environment) {
    try {
        def functions = ['data-processor', 'data-scheduler']
        def allHealthy = true
        
        functions.each { functionName ->
            def fullName = "f1-data-platform-${environment}-${functionName}"
            
            try {
                def functionState = sh(
                    script: "aws lambda get-function --function-name ${fullName} --query 'Configuration.State' --output text",
                    returnStdout: true
                ).trim()
                
                if (functionState != 'Active') {
                    echo "⚠️ Lambda function ${fullName} is not active: ${functionState}"
                    allHealthy = false
                }
            } catch (Exception e) {
                echo "⚠️ Lambda function ${fullName} not found or inaccessible"
                allHealthy = false
            }
        }
        
        if (allHealthy) {
            echo "✅ All Lambda functions are healthy"
            return 'PASS'
        } else {
            return 'WARNING'
        }
    } catch (Exception e) {
        echo "⚠️ Lambda functions check failed: ${e.getMessage()}"
        return 'WARNING'
    }
}

def checkCloudWatchMonitoring(String environment) {
    try {
        def dashboardName = "f1-data-platform-${environment}-analytics"
        
        sh "aws cloudwatch get-dashboard --dashboard-name ${dashboardName}"
        
        echo "✅ CloudWatch monitoring is configured"
        return 'PASS'
    } catch (Exception e) {
        echo "⚠️ CloudWatch monitoring check failed: ${e.getMessage()}"
        return 'WARNING'
    }
}

def runAzureHealthChecks(String environment, int timeout) {
    echo "🔵 Azure health checks not yet implemented"
    return [status: 'NOT_IMPLEMENTED', checks: [:], message: 'Azure health checks not implemented']
}

def runGCPHealthChecks(String environment, int timeout) {
    echo "🔴 GCP health checks not yet implemented"
    return [status: 'NOT_IMPLEMENTED', checks: [:], message: 'GCP health checks not implemented']
}

def runSmokeTests(Map config) {
    echo "🧪 Running smoke tests..."
    
    def clouds = config.clouds ?: ['aws']
    def environment = config.environment ?: 'dev'
    
    def smokeResults = [:]
    
    clouds.each { cloud ->
        echo "💨 Running smoke tests for ${cloud}..."
        smokeResults[cloud] = runCloudSmokeTests(cloud, environment)
    }
    
    // Generate smoke test report
    generateSmokeTestReport(smokeResults, environment)
    
    echo "✅ Smoke tests completed"
    return smokeResults
}

def runCloudSmokeTests(String cloud, String environment) {
    def result = [status: 'PASS', tests: [:]]
    
    try {
        switch(cloud) {
            case 'aws':
                result = runAWSSmokeTests(environment)
                break
            case 'azure':
                result = [status: 'NOT_IMPLEMENTED', tests: [:]]
                break
            case 'gcp':
                result = [status: 'NOT_IMPLEMENTED', tests: [:]]
                break
        }
    } catch (Exception e) {
        result.status = 'FAIL'
        result.error = e.getMessage()
    }
    
    return result
}

def runAWSSmokeTests(String environment) {
    echo "☁️ Running AWS smoke tests..."
    
    def result = [status: 'PASS', tests: [:]]
    def tests = [:]
    
    try {
        // Test S3 data operations
        tests['s3_data_operations'] = testS3DataOperations(environment)
        
        // Test Lambda function invocation
        tests['lambda_invocation'] = testLambdaInvocation(environment)
        
        // Test Athena query execution
        tests['athena_query'] = testAthenaQuery(environment)
        
        // Test Glue crawler (if not running)
        tests['glue_crawler_test'] = testGlueCrawler(environment)
        
        result.tests = tests
        
        // Check if any tests failed
        def failedTests = tests.findAll { name, status -> status != 'PASS' }
        if (failedTests) {
            result.status = 'FAIL'
            result.message = "Failed tests: ${failedTests.keySet().join(', ')}"
        }
        
    } catch (Exception e) {
        result.status = 'ERROR'
        result.error = e.getMessage()
    }
    
    return result
}

def testS3DataOperations(String environment) {
    try {
        def bucketName = "f1-data-lake-${environment}-\${AWS_ACCOUNT_ID}"
        def testFile = "smoke-test-${env.BUILD_NUMBER}.txt"
        def testContent = "Smoke test from build ${env.BUILD_NUMBER} at ${new Date()}"
        
        // Write test file
        writeFile file: testFile, text: testContent
        
        // Upload to S3
        sh "aws s3 cp ${testFile} s3://${bucketName}/smoke-tests/"
        
        // Download from S3
        sh "aws s3 cp s3://${bucketName}/smoke-tests/${testFile} ${testFile}.downloaded"
        
        // Verify content
        def downloadedContent = readFile("${testFile}.downloaded")
        if (downloadedContent.trim() == testContent) {
            echo "✅ S3 data operations test passed"
            
            // Cleanup test files
            sh "aws s3 rm s3://${bucketName}/smoke-tests/${testFile}"
            sh "rm -f ${testFile} ${testFile}.downloaded"
            
            return 'PASS'
        } else {
            echo "❌ S3 data operations test failed - content mismatch"
            return 'FAIL'
        }
        
    } catch (Exception e) {
        echo "❌ S3 data operations test failed: ${e.getMessage()}"
        return 'FAIL'
    }
}

def testLambdaInvocation(String environment) {
    try {
        def functionName = "f1-data-platform-${environment}-data-processor"
        
        // Test synchronous invocation
        def response = sh(
            script: """
            aws lambda invoke \
                --function-name ${functionName} \
                --payload '{"test": true, "source": "smoke-test"}' \
                --output text \
                response.json
            """,
            returnStdout: true
        ).trim()
        
        echo "✅ Lambda invocation test passed"
        return 'PASS'
        
    } catch (Exception e) {
        echo "⚠️ Lambda invocation test failed: ${e.getMessage()}"
        return 'WARNING'  // Not critical for smoke tests
    }
}

def testAthenaQuery(String environment) {
    try {
        def workgroupName = "f1-data-platform-${environment}-analytics"
        def database = "f1_data_${environment}"
        
        // Simple test query
        def queryString = "SHOW TABLES IN ${database};"
        
        def queryExecution = sh(
            script: """
            aws athena start-query-execution \
                --query-string "${queryString}" \
                --work-group ${workgroupName} \
                --query 'QueryExecutionId' \
                --output text
            """,
            returnStdout: true
        ).trim()
        
        // Wait for query completion (up to 30 seconds)
        timeout(time: 30, unit: 'SECONDS') {
            waitUntil {
                def status = sh(
                    script: "aws athena get-query-execution --query-execution-id ${queryExecution} --query 'QueryExecution.Status.State' --output text",
                    returnStdout: true
                ).trim()
                
                return status == 'SUCCEEDED' || status == 'FAILED' || status == 'CANCELLED'
            }
        }
        
        def finalStatus = sh(
            script: "aws athena get-query-execution --query-execution-id ${queryExecution} --query 'QueryExecution.Status.State' --output text",
            returnStdout: true
        ).trim()
        
        if (finalStatus == 'SUCCEEDED') {
            echo "✅ Athena query test passed"
            return 'PASS'
        } else {
            echo "❌ Athena query test failed with status: ${finalStatus}"
            return 'FAIL'
        }
        
    } catch (Exception e) {
        echo "⚠️ Athena query test failed: ${e.getMessage()}"
        return 'WARNING'
    }
}

def testGlueCrawler(String environment) {
    try {
        def crawlerName = "f1-data-crawler-${environment}"
        
        def crawlerState = sh(
            script: "aws glue get-crawler --name ${crawlerName} --query 'Crawler.State' --output text",
            returnStdout: true
        ).trim()
        
        if (crawlerState == 'READY') {
            echo "✅ Glue crawler test passed - crawler is ready"
            return 'PASS'
        } else {
            echo "⚠️ Glue crawler is in state: ${crawlerState}"
            return 'WARNING'
        }
        
    } catch (Exception e) {
        echo "⚠️ Glue crawler test failed: ${e.getMessage()}"
        return 'WARNING'
    }
}

def runPerformanceTests(Map config) {
    echo "⚡ Running performance tests..."
    
    def clouds = config.clouds ?: ['aws']
    def environment = config.environment ?: 'dev'
    def duration = config.duration ?: 300 // 5 minutes
    
    def performanceResults = [:]
    
    clouds.each { cloud ->
        echo "📊 Running performance tests for ${cloud}..."
        performanceResults[cloud] = runCloudPerformanceTests(cloud, environment, duration)
    }
    
    // Generate performance test report
    generatePerformanceReport(performanceResults, environment)
    
    echo "✅ Performance tests completed"
    return performanceResults
}

def runCloudPerformanceTests(String cloud, String environment, int duration) {
    def result = [status: 'PASS', metrics: [:]]
    
    try {
        switch(cloud) {
            case 'aws':
                result = runAWSPerformanceTests(environment, duration)
                break
            case 'azure':
                result = [status: 'NOT_IMPLEMENTED', metrics: [:]]
                break
            case 'gcp':
                result = [status: 'NOT_IMPLEMENTED', metrics: [:]]
                break
        }
    } catch (Exception e) {
        result.status = 'ERROR'
        result.error = e.getMessage()
    }
    
    return result
}

def runAWSPerformanceTests(String environment, int duration) {
    echo "☁️ Running AWS performance tests..."
    
    def result = [status: 'PASS', metrics: [:]]
    
    try {
        // Test S3 upload/download performance
        result.metrics['s3_performance'] = testS3Performance(environment)
        
        // Test Lambda cold start time
        result.metrics['lambda_performance'] = testLambdaPerformance(environment)
        
        // Test Athena query performance
        result.metrics['athena_performance'] = testAthenaPerformance(environment)
        
        echo "✅ AWS performance tests completed"
        
    } catch (Exception e) {
        result.status = 'ERROR'
        result.error = e.getMessage()
    }
    
    return result
}

def testS3Performance(String environment) {
    echo "📊 Testing S3 performance..."
    
    def bucketName = "f1-data-lake-${environment}-\${AWS_ACCOUNT_ID}"
    def testFile = "performance-test-${env.BUILD_NUMBER}.dat"
    
    try {
        // Create a test file (10MB)
        sh "dd if=/dev/zero of=${testFile} bs=1M count=10 2>/dev/null"
        
        // Measure upload time
        def uploadStart = System.currentTimeMillis()
        sh "aws s3 cp ${testFile} s3://${bucketName}/performance-tests/"
        def uploadTime = System.currentTimeMillis() - uploadStart
        
        // Measure download time
        def downloadStart = System.currentTimeMillis()
        sh "aws s3 cp s3://${bucketName}/performance-tests/${testFile} ${testFile}.downloaded"
        def downloadTime = System.currentTimeMillis() - downloadStart
        
        // Cleanup
        sh "aws s3 rm s3://${bucketName}/performance-tests/${testFile}"
        sh "rm -f ${testFile} ${testFile}.downloaded"
        
        def metrics = [
            upload_time_ms: uploadTime,
            download_time_ms: downloadTime,
            file_size_mb: 10
        ]
        
        echo "📈 S3 Performance: Upload ${uploadTime}ms, Download ${downloadTime}ms"
        return metrics
        
    } catch (Exception e) {
        echo "❌ S3 performance test failed: ${e.getMessage()}"
        return [error: e.getMessage()]
    }
}

def testLambdaPerformance(String environment) {
    echo "⚡ Testing Lambda performance..."
    
    def functionName = "f1-data-platform-${environment}-data-processor"
    
    try {
        def invocations = []
        
        // Run 3 invocations to test cold start and warm execution
        for (int i = 0; i < 3; i++) {
            def start = System.currentTimeMillis()
            
            sh """
            aws lambda invoke \
                --function-name ${functionName} \
                --payload '{"test": true, "iteration": ${i}}' \
                --output text \
                response-${i}.json
            """
            
            def duration = System.currentTimeMillis() - start
            invocations.add(duration)
            
            if (i < 2) {
                sleep(2) // Brief pause between invocations
            }
        }
        
        def metrics = [
            cold_start_ms: invocations[0],
            warm_execution_1_ms: invocations[1],
            warm_execution_2_ms: invocations[2],
            average_ms: invocations.sum() / invocations.size()
        ]
        
        echo "📈 Lambda Performance: Cold start ${invocations[0]}ms, Warm avg ${(invocations[1] + invocations[2]) / 2}ms"
        return metrics
        
    } catch (Exception e) {
        echo "❌ Lambda performance test failed: ${e.getMessage()}"
        return [error: e.getMessage()]
    }
}

def testAthenaPerformance(String environment) {
    echo "🔍 Testing Athena performance..."
    
    // This would be implemented with actual queries against your F1 data
    echo "⚠️ Athena performance testing requires sample data"
    return [status: 'SKIPPED', reason: 'No sample data available']
}

def generateHealthReport(Map healthResults, String environment) {
    echo "📋 Generating health check report..."
    
    def report = [
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
        environment: environment,
        build: env.BUILD_NUMBER,
        results: healthResults
    ]
    
    writeJSON file: "health-report-${environment}.json", json: report
    
    // Create HTML report
    def htmlReport = generateHealthReportHTML(report)
    writeFile file: "health-report-${environment}.html", text: htmlReport
    
    // Archive reports
    archiveArtifacts artifacts: "health-report-${environment}.*", allowEmptyArchive: true
    
    publishHTML([
        allowMissing: false,
        alwaysLinkToLastBuild: false,
        keepAll: true,
        reportDir: '.',
        reportFiles: "health-report-${environment}.html",
        reportName: 'Health Check Report'
    ])
}

def generateSmokeTestReport(Map smokeResults, String environment) {
    echo "📋 Generating smoke test report..."
    
    def report = [
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
        environment: environment,
        build: env.BUILD_NUMBER,
        results: smokeResults
    ]
    
    writeJSON file: "smoke-test-report-${environment}.json", json: report
    archiveArtifacts artifacts: "smoke-test-report-${environment}.json", allowEmptyArchive: true
}

def generatePerformanceReport(Map performanceResults, String environment) {
    echo "📋 Generating performance test report..."
    
    def report = [
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
        environment: environment,
        build: env.BUILD_NUMBER,
        results: performanceResults
    ]
    
    writeJSON file: "performance-report-${environment}.json", json: report
    archiveArtifacts artifacts: "performance-report-${environment}.json", allowEmptyArchive: true
}

def generateHealthReportHTML(Map report) {
    return """
<!DOCTYPE html>
<html>
<head>
    <title>F1 Data Platform Health Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background: #f5f5f5; padding: 15px; border-radius: 5px; }
        .status-pass { color: green; font-weight: bold; }
        .status-warning { color: orange; font-weight: bold; }
        .status-critical { color: red; font-weight: bold; }
        .status-error { color: red; font-weight: bold; }
        table { border-collapse: collapse; width: 100%; margin: 20px 0; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <div class="header">
        <h1>🏎️ F1 Data Platform Health Report</h1>
        <p><strong>Environment:</strong> ${report.environment}</p>
        <p><strong>Timestamp:</strong> ${report.timestamp}</p>
        <p><strong>Build:</strong> ${report.build}</p>
    </div>
    
    <h2>Health Check Results</h2>
    <table>
        <tr><th>Cloud Provider</th><th>Overall Status</th><th>Duration (ms)</th><th>Details</th></tr>
        ${report.results.collect { cloud, result ->
            def statusClass = "status-${result.status.toLowerCase().replace('_', '-')}"
            return "<tr><td>${cloud}</td><td class='${statusClass}'>${result.status}</td><td>${result.duration}</td><td>${result.message ?: 'OK'}</td></tr>"
        }.join('\n')}
    </table>
</body>
</html>
"""
}

// Export functions for Jenkins pipeline use
return this