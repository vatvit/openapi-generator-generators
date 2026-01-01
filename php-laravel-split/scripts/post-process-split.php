#!/usr/bin/env php
<?php

declare(strict_types=1);

/**
 * Post-process hook for OpenAPI Generator
 *
 * Splits a combined controller file by markers into separate per-operation files.
 *
 * Usage: Set as PHP_POST_PROCESS_FILE environment variable
 *   export PHP_POST_PROCESS_FILE="/path/to/post-process-split.php"
 *   openapi-generator-cli generate ... --enable-post-process-file
 *
 * The generator passes the generated file path as the first argument.
 */

if ($argc < 2) {
    // No file provided - nothing to do
    exit(0);
}

$filePath = $argv[1];

// Only process PHP files
if (!str_ends_with($filePath, '.php')) {
    exit(0);
}

// Check if file exists
if (!file_exists($filePath)) {
    exit(0);
}

$content = file_get_contents($filePath);
$marker = '---SPLIT:';

// If no markers, leave file as-is
if (strpos($content, $marker) === false) {
    exit(0);
}

$outputDir = dirname($filePath);

// Split by marker pattern
$pattern = '/---SPLIT:([^-]+)---/';
$parts = preg_split($pattern, $content, -1, PREG_SPLIT_DELIM_CAPTURE);

// Parts array: [content0, filename1, content1, filename2, content2, ...]
$splitCount = 0;

for ($i = 0; $i < count($parts) - 1; $i += 2) {
    $classContent = trim($parts[$i]);

    if ($i + 1 < count($parts)) {
        $filename = trim($parts[$i + 1]);

        if (!empty($classContent) && !empty($filename)) {
            $outputPath = $outputDir . '/' . $filename;
            file_put_contents($outputPath, $classContent . "\n");
            $splitCount++;
            fwrite(STDERR, "  [post-process] Created: $filename\n");
        }
    }
}

// Delete original combined file after splitting
if ($splitCount > 0) {
    unlink($filePath);
    fwrite(STDERR, "  [post-process] Removed combined file: " . basename($filePath) . "\n");
}
