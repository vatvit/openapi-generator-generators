#!/usr/bin/env php
<?php

declare(strict_types=1);

/**
 * Split combined controller files by marker
 *
 * Usage: php split-files.php <input-dir> <output-dir>
 *
 * Scans input directory for files containing ---SPLIT:filename--- markers,
 * splits them into separate files, and writes to output directory.
 */

if ($argc < 3) {
    echo "Usage: php split-files.php <input-dir> <output-dir>\n";
    exit(1);
}

$inputDir = rtrim($argv[1], '/');
$outputDir = rtrim($argv[2], '/');

if (!is_dir($inputDir)) {
    echo "Error: Input directory does not exist: $inputDir\n";
    exit(1);
}

// Create output directory if it doesn't exist
if (!is_dir($outputDir)) {
    mkdir($outputDir, 0755, true);
}

$marker = '---SPLIT:';
$markerEnd = '---';
$splitCount = 0;
$fileCount = 0;

// Find all PHP files in input directory
$files = glob($inputDir . '/*.php');

foreach ($files as $file) {
    $content = file_get_contents($file);

    // Check if file contains split markers
    if (strpos($content, $marker) === false) {
        // No markers, copy file as-is
        copy($file, $outputDir . '/' . basename($file));
        continue;
    }

    $fileCount++;

    // Split by marker pattern
    $pattern = '/---SPLIT:([^-]+)---/';
    $parts = preg_split($pattern, $content, -1, PREG_SPLIT_DELIM_CAPTURE);

    // Parts array: [content0, filename1, content1, filename2, content2, ...]
    // Even indices (0, 2, 4...) are content
    // Odd indices (1, 3, 5...) are filenames

    for ($i = 0; $i < count($parts) - 1; $i += 2) {
        $classContent = trim($parts[$i]);

        if ($i + 1 < count($parts)) {
            $filename = trim($parts[$i + 1]);

            if (!empty($classContent) && !empty($filename)) {
                $outputPath = $outputDir . '/' . $filename;
                file_put_contents($outputPath, $classContent . "\n");
                $splitCount++;
                echo "  Created: $filename\n";
            }
        }
    }

    // Delete original combined file after splitting
    unlink($file);
    echo "  Removed combined file: " . basename($file) . "\n";
}

echo "\nSplit complete: $splitCount files created from $fileCount combined files\n";
