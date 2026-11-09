package dev.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in real Docker validation. A stopped or missing engine fails; it never falls back to host execution. */
@EnabledIfEnvironmentVariable(named="WORKSPACE_DOCKER_TEST_EXECUTABLE",matches=".+")
class DockerCompatibilityTests {
    @TempDir Path root;
    @Test void realLinuxContainerRunsChecksWithReadOnlySourceAndNoHostAccess() throws Exception {
        Path staging=Files.createDirectory(root.resolve("staging"));
        Files.writeString(staging.resolve("greeting.mjs"),"export const greeting = (name) => `Hello, ${name}`;\n");
        Files.writeString(staging.resolve("greeting.test.mjs"),"""
            import test from 'node:test';
            import assert from 'node:assert/strict';
            import { writeFileSync, existsSync } from 'node:fs';
            import { networkInterfaces } from 'node:os';
            import { greeting } from './greeting.mjs';
            test('greeting works', () => assert.equal(greeting('Ada'), 'Hello, Ada'));
            test('source mount is read only', () => assert.throws(() => writeFileSync('/workspace/escape.txt', 'no')));
            test('root filesystem is protected', () => assert.throws(() => writeFileSync('/etc/escape.txt', 'no')));
            test('no daemon socket or account credentials', () => {
              assert.equal(existsSync('/var/run/docker.sock'), false);
              assert.equal(existsSync('/root/.codex/auth.json'), false);
            });
            test('network namespace has no external interface', () => {
              assert.equal(Object.values(networkInterfaces()).flat().filter((entry) => !entry.internal).length, 0);
            });
            """);
        DockerSandbox sandbox=new DockerSandbox(new BoundedProcess(),true,System.getenv("WORKSPACE_DOCKER_TEST_EXECUTABLE"),Objects.requireNonNullElse(System.getenv("WORKSPACE_DOCKER_CONTEXT"),""),root.toString());
        DockerSandbox.Status status=sandbox.status(true);assertThat(status.available()).as(status.message()).isTrue();
        UUID passing=UUID.randomUUID(),failing=UUID.randomUUID();
        try {
            var result=sandbox.check(passing,staging,status.imageId(),()->false,s->{});
            assertThat(result.exitCode()).isZero();assertThat(result.tests()).isEqualTo(5);
            assertThat(sandbox.cleanup(passing)).isTrue();
            Files.writeString(staging.resolve("greeting.test.mjs"),"import test from 'node:test';import assert from 'node:assert/strict';test('intentional failure',()=>assert.fail('fixture failure'));\n");
            var failed=sandbox.check(failing,staging,status.imageId(),()->false,s->{});
            assertThat(failed.exitCode()).isNotZero();assertThat(failed.tests()).isEqualTo(1);assertThat(failed.logs()).contains("fixture failure");
        } finally {assertThat(sandbox.cleanup(passing)).isTrue();assertThat(sandbox.cleanup(failing)).isTrue();}
    }
}
