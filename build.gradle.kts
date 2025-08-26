plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    id("jacoco")
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

allprojects {
    group = property("app.group").toString()
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.dependencies.get().toString())
    }
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.spring.boot.configuration.processor)
    testImplementation(libs.spring.boot.starter.test)
}

// about source and compilation
java {
    sourceCompatibility = JavaVersion.VERSION_17
}

// JaCoCo
extensions.getByType<JacocoPluginExtension>().apply {
    toolVersion = "0.8.7"
}

// bundling tasks
tasks.named("bootJar") { enabled = true }
tasks.named("jar") { enabled = false }

// test tasks
tasks.test {
    useJUnitPlatform()

    // 표준 출력/에러 로그, 통과/실패 이벤트 모두 콘솔에 노출
    testLogging {
        // passed/failed/skipped + 표준 스트림
        events("passed", "failed", "skipped", "standardOut", "standardError")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
    }

    // 자바 기본 파일 인코딩
    systemProperty("file.encoding", "UTF-8")

    outputs.upToDateWhen { false }
}
