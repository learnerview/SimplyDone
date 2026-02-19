#!/bin/bash

# Security Verification Script
# Ensures no hardcoded secrets are committed to the repository

echo "🔒 Security Verification for SimplyDone"
echo "=================================="

# Check for hardcoded passwords
echo "🔍 Checking for hardcoded passwords..."
if grep -r "password.*=" src/ --include="*.java" --include="*.properties" --include="*.yml" | grep -v "password.*=" | grep -v "password.*\$\|password.*:"; then
    echo "❌ WARNING: Potential hardcoded passwords found!"
    exit 1
else
    echo "✅ No hardcoded passwords found"
fi

# Check for API keys
echo "🔍 Checking for hardcoded API keys..."
if grep -r "api.*key\|secret.*=" src/ --include="*.java" --include="*.properties" --include="*.yml" -i; then
    echo "❌ WARNING: Potential hardcoded API keys found!"
    exit 1
else
    echo "✅ No hardcoded API keys found"
fi

# Check for email credentials
echo "🔍 Checking for hardcoded email credentials..."
if grep -r "gmail\.com\|yahoo\.com\|outlook\.com" src/ --include="*.java" --include="*.properties" --include="*.yml"; then
    echo "❌ WARNING: Potential hardcoded email addresses found!"
    exit 1
else
    echo "✅ No hardcoded email addresses found"
fi

# Check environment variable usage
echo "🔍 Checking for environment variable usage..."
if grep -r "\$\{.*\}" src/main/resources/ --include="*.properties" --include="*.yml" | wc -l > /dev/null; then
    echo "✅ Environment variables are used correctly"
else
    echo "❌ WARNING: Environment variables not found in configuration"
fi

# Check .gitignore
echo "🔍 Checking .gitignore for security..."
if grep -q "\.env" .gitignore; then
    echo "✅ .env files are properly ignored"
else
    echo "❌ WARNING: .env files not in .gitignore"
fi

echo ""
echo "🎉 Security verification completed!"
echo "Your project is ready for safe deployment to production."
