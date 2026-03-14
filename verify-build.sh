#!/bin/bash

echo "🔍 Verifying backend build..."

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed or not in PATH"
    exit 1
fi

echo "✅ Maven is available"

# Check if Java 17 is available
java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$java_version" -lt 17 ]; then
    echo "❌ Java 17 or higher is required (found Java $java_version)"
    exit 1
fi

echo "✅ Java $java_version is available"

# Check key files exist
key_files=(
    "pom.xml"
    "src/main/java/com/company/company_clean_hub_be/CompanyCleanHubBeApplication.java"
    "src/main/java/com/company/company_clean_hub_be/service/impl/EvaluationServiceImpl.java"
    "src/main/java/com/company/company_clean_hub_be/repository/EvaluationRepository.java"
)

for file in "${key_files[@]}"; do
    if [ -f "$file" ]; then
        echo "✅ $file"
    else
        echo "❌ $file - MISSING"
        exit 1
    fi
done

# Compile the project
echo ""
echo "🔨 Compiling project..."
if mvn clean compile -q; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

# Run tests
echo ""
echo "🧪 Running tests..."
if mvn test -q; then
    echo "✅ All tests passed"
else
    echo "❌ Tests failed"
    exit 1
fi

echo ""
echo "🎉 Backend build verification PASSED!"
echo ""
echo "To run the application:"
echo "  mvn spring-boot:run    # Start the Spring Boot application"
echo "  mvn clean package      # Build JAR file"
echo "  mvn test               # Run tests only"