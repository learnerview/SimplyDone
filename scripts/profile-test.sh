#!/bin/bash

echo "🧪 Comprehensive Profile Testing for SimplyDone"
echo "=========================================="

# Test 1: Default Profile (without database)
echo ""
echo "🔧 Testing Default Profile..."
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=default" &
DEFAULT_PID=$!
sleep 10

# Check if default profile started
if ps -p $DEFAULT_PID > /dev/null; then
    echo "✅ Default profile started successfully"
    kill $DEFAULT_PID
else
    echo "❌ Default profile failed to start"
fi

# Test 2: Test Profile (with H2)
echo ""
echo "🔧 Testing Test Profile..."
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=test" &
TEST_PID=$!
sleep 10

# Check if test profile started
if ps -p $TEST_PID > /dev/null; then
    echo "✅ Test profile started successfully"
    kill $TEST_PID
else
    echo "❌ Test profile failed to start"
fi

# Test 3: Production Profile (without external services)
echo ""
echo "🔧 Testing Production Profile (without external services)..."
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=prod --simplydone.enhanced-executor=false --simplydone.enhanced-rate-limiting=false --simplydone.enhanced-retry=false" &
PROD_PID=$!
sleep 10

# Check if prod profile started
if ps -p $PROD_PID > /dev/null; then
    echo "✅ Production profile started successfully"
    kill $PROD_PID
else
    echo "❌ Production profile failed to start"
fi

# Test 4: Enhanced Features Check
echo ""
echo "🔧 Checking Enhanced Features Configuration..."

# Check if enhanced features are properly configured
echo "📋 Enhanced Features Configuration:"
grep -n "simplydone.enhanced" src/main/resources/application.properties
echo ""
grep -n "simplydone.enhanced" src/main/resources/application-prod.properties

# Test 5: Security Check
echo ""
echo "🔒 Running Security Verification..."
if [ -f "scripts/security-check.sh" ]; then
    chmod +x scripts/security-check.sh
    ./scripts/security-check.sh
else
    echo "❌ Security check script not found"
fi

# Test 6: Configuration Files Check
echo ""
echo "📋 Configuration Files Verification:"
echo "✅ application.properties exists: $([ -f "src/main/resources/application.properties" ] && echo "YES" || echo "NO")"
echo "✅ application-prod.properties exists: $([ -f "src/main/resources/application-prod.properties" ] && echo "YES" || echo "NO")"
echo "✅ application-test.properties exists: $([ -f "src/test/resources/application-test.properties" ] && echo "YES" || echo "NO")"
echo "✅ render.yaml exists: $([ -f "render.yaml" ] && echo "YES" || echo "NO")"
echo "✅ .env.example exists: $([ -f ".env.example" ] && echo "YES" || echo "NO")"
echo "✅ .gitignore exists: $([ -f ".gitignore" ] && echo "YES" || echo "NO")"

# Test 7: Database Configuration Check
echo ""
echo "🗄️ Database Configuration Check:"
echo "✅ PostgreSQL dependency: $(grep -c "postgresql" pom.xml)"
echo "✅ H2 dependency: $(grep -c "h2database" pom.xml)"
echo "✅ JPA dependency: $(grep -c "spring-boot-starter-data-jpa" pom.xml)"

# Test 8: Enhanced Features Count
echo ""
echo "⚡ Enhanced Features Count:"
echo "✅ Job Execution Strategies: $(find src/main/java -name "*JobExecutionStrategy.java" | wc -l)"
echo "✅ Database Entities: $(find src/main/java -name "*Entity.java" | wc -l)"
echo "✅ Repository Classes: $(find src/main/java -name "*Repository.java" | wc -l)"
echo "✅ Service Classes: $(find src/main/java -name "*Service*.java" | wc -l)"

echo ""
echo "🎉 Profile Testing Completed!"
echo "============================"
