#!/bin/bash

# Post-process hook for OpenAPI Generator
# Splits a combined controller file by markers into separate per-operation files.
#
# Usage: Set as PHP_POST_PROCESS_FILE environment variable
#   export PHP_POST_PROCESS_FILE="/path/to/post-process-split.sh"
#   openapi-generator-cli generate ... --enable-post-process-file

FILE_PATH="$1"

# Only process PHP files
if [[ ! "$FILE_PATH" == *.php ]]; then
    exit 0
fi

# Check if file exists
if [[ ! -f "$FILE_PATH" ]]; then
    exit 0
fi

# Check if file contains split markers
if ! grep -q '---SPLIT:' "$FILE_PATH"; then
    exit 0
fi

OUTPUT_DIR=$(dirname "$FILE_PATH")
CONTENT=$(cat "$FILE_PATH")

# Split by marker and create separate files
# Using awk to split on the marker pattern
awk '
BEGIN {
    RS="---SPLIT:[^-]+---"
    FS="---SPLIT:"
    filecount=0
}
{
    if (NR > 1 || length($0) > 10) {
        # Get content before marker
        content = $0
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", content)

        if (length(content) > 0 && filecount > 0) {
            print content > (ENVIRON["OUTPUT_DIR"] "/" ENVIRON["FILENAME_" filecount])
            print "  [post-process] Created: " ENVIRON["FILENAME_" filecount] > "/dev/stderr"
        }
        filecount++
    }
}
' "$FILE_PATH"

# Extract filenames from markers
FILENAMES=$(grep -o '---SPLIT:[^-]*---' "$FILE_PATH" | sed 's/---SPLIT:\([^-]*\)---/\1/')

# Split content and write files
IFS=$'\n'
MARKER="---SPLIT:"
MARKER_END="---"

# Use csplit approach - more reliable
cd "$OUTPUT_DIR"

# Create temporary file for processing
TEMPFILE=$(mktemp)
cp "$FILE_PATH" "$TEMPFILE"

# Extract each section
SECTION_NUM=0
PREV_POS=0

while IFS= read -r line; do
    if [[ "$line" =~ ---SPLIT:([^-]+)--- ]]; then
        FILENAME="${BASH_REMATCH[1]}"
        # Get content up to this marker
        if [[ $SECTION_NUM -gt 0 ]]; then
            echo "  [post-process] Would create: $FILENAME" >&2
        fi
        ((SECTION_NUM++))
    fi
done < "$FILE_PATH"

# Simple approach: use sed to extract sections
# Get all filenames
mapfile -t FILENAMES < <(grep -o '---SPLIT:[^-]*---' "$FILE_PATH" | sed 's/---SPLIT://;s/---//')

# Split the file by markers
csplit -sf "${OUTPUT_DIR}/part_" -z "$FILE_PATH" '/---SPLIT:/' '{*}' 2>/dev/null || true

# Rename parts to their proper filenames
PART_NUM=0
for PART_FILE in "${OUTPUT_DIR}"/part_*; do
    if [[ -f "$PART_FILE" ]]; then
        if [[ $PART_NUM -lt ${#FILENAMES[@]} ]]; then
            DEST_NAME="${FILENAMES[$PART_NUM]}"
            # Remove the marker line from the file
            sed -i.bak '/---SPLIT:/d' "$PART_FILE" 2>/dev/null || sed '/---SPLIT:/d' "$PART_FILE" > "${PART_FILE}.tmp" && mv "${PART_FILE}.tmp" "$PART_FILE"
            # Trim whitespace
            sed -i.bak '/^[[:space:]]*$/d' "$PART_FILE" 2>/dev/null || true
            rm -f "${PART_FILE}.bak" 2>/dev/null

            # Move to final name
            if [[ -n "$DEST_NAME" && -s "$PART_FILE" ]]; then
                mv "$PART_FILE" "${OUTPUT_DIR}/${DEST_NAME}"
                echo "  [post-process] Created: $DEST_NAME" >&2
            else
                rm -f "$PART_FILE"
            fi
        else
            rm -f "$PART_FILE"
        fi
        ((PART_NUM++))
    fi
done

rm -f "$TEMPFILE"

# Remove original combined file
rm -f "$FILE_PATH"
echo "  [post-process] Removed combined file: $(basename "$FILE_PATH")" >&2
