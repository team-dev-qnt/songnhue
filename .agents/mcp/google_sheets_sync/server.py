import re
import os
import json
from fastmcp import FastMCP
from google.oauth2 import service_account
from googleapiclient.discovery import build
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("google_sheets_sync")

# Initialize FastMCP server
mcp = FastMCP("GoogleSheetsSync")

def parse_markdown_to_data(input_path):
    tasks = []
    
    status_map_list = {
        'x': 'Done',
        ' ': 'Pending',
        '~': 'In Progress',
        '✅': 'Done',
        '🟡': 'In Progress',
        '⬜': 'Pending',
        '❌': 'Cancelled',
        '⏸': 'Paused'
    }
    
    task_list_pattern = re.compile(r'^\s*-\s+\[(x| |~|✅|🟡|⬜|❌|⏸)\]\s+(.*)$')
    table_row_pattern = re.compile(r'^\s*\|\s*([^|]+)\s*\|\s*([^|]+)\s*\|\s*([^|]+)\s*\|(.*)\|$')
    
    current_ws = ""
    ws_pattern = re.compile(r'^##\s+(WS-\d+|VM-\d+|Phase.*|\d+\.\s+WS-\d+).*')
    
    in_table = False
    
    if not os.path.exists(input_path):
        return tasks
        
    with open(input_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
        
        for line in lines:
            line = line.strip()
            
            ws_match = ws_pattern.match(line)
            if ws_match:
                current_ws = ws_match.group(1).strip()
                continue
            
            if re.match(r'^\s*\|[-:| ]+\|\s*[-:| ]+\|\s*[-:| ]+', line):
                in_table = True
                continue
                
            list_match = task_list_pattern.match(line)
            if list_match:
                status_char = list_match.group(1)
                status = status_map_list.get(status_char, 'Unknown')
                content = list_match.group(2).strip()
                
                task_id_match = re.search(r'\*\*(T\d+\.\d+[a-z]?|M\d+\.\d+|WS-\d+|Phase.*)\*\*', content)
                task_id = ""
                if task_id_match:
                    task_id = task_id_match.group(1)
                    content = content.replace(f'**{task_id}**', '').strip()
                else:
                    task_id_match2 = re.match(r'^(T\d+\.\d+[a-z]?)\s+(.*)', content)
                    if task_id_match2:
                        task_id = task_id_match2.group(1)
                        content = task_id_match2.group(2).strip()
                
                if '✅' in content:
                    status = 'Done'
                elif '🟡' in content:
                    status = 'In Progress'
                
                tasks.append([current_ws, task_id, content.strip(' -*'), status, ''])
                continue
                
            table_match = table_row_pattern.match(line)
            if table_match and in_table:
                id_col = table_match.group(1).strip()
                if id_col.lower() in ['id', 'mã', 'ws', 'task']:
                    continue
                    
                desc_col = table_match.group(2).strip()
                status_col = table_match.group(3).strip()
                note_col = table_match.group(4).strip() if table_match.group(4) else ""
                
                if id_col.startswith('T') or id_col.startswith('WS') or re.match(r'^T\d+\.\d+', id_col):
                    status = status_col
                    if '✅' in status_col or 'Xong' in status_col:
                        status = 'Done'
                    elif '🟡' in status_col or 'Đang làm' in status_col:
                        status = 'In Progress'
                    elif '⬜' in status_col or 'Chưa làm' in status_col:
                        status = 'Pending'
                    elif '❌' in status_col:
                        status = 'Cancelled'
                    elif '⏸' in status_col:
                        status = 'Paused'
                            
                    tasks.append([current_ws, id_col, desc_col, status, note_col])

    return tasks

@mcp.tool()
def sync_markdown_to_sheets() -> str:
    """
    Parses phase0-tracking.md, phase1-tracking.md, and phase1-execution-tracking.md
    and syncs the task list directly to the Google Sheet (ID: 1RCaa9ak3q6KcbXTjjgvp7DGFuAb914DYUlnbvTlBpdY).
    Requires .claude/google-credentials.json to be present.
    """
    logger.info("Starting sync_markdown_to_sheets tool...")
    
    # Configuration
    SHEET_ID = "1RCaa9ak3q6KcbXTjjgvp7DGFuAb914DYUlnbvTlBpdY"
    
    # Path is relative to the root of the project
    CREDENTIALS_PATH = os.path.join(os.getcwd(), ".claude", "google-credentials.json")
    
    if not os.path.exists(CREDENTIALS_PATH):
        return f"Error: Credentials file not found at {CREDENTIALS_PATH}. Please provide it."

    # Parse all three markdown files
    all_data = [["Phase/WS", "Task ID", "Description", "Status", "Notes"]]
    
    md_files = [
        ".claude/master-tracking.md"
    ]
    
    for f in md_files:
        filepath = os.path.join(os.getcwd(), f)
        tasks = parse_markdown_to_data(filepath)
        all_data.extend(tasks)

    logger.info(f"Parsed {len(all_data)-1} tasks from markdown files.")

    try:
        # Authenticate with Google Sheets API
        scopes = ['https://www.googleapis.com/auth/spreadsheets']
        creds = service_account.Credentials.from_service_account_file(CREDENTIALS_PATH, scopes=scopes)
        service = build('sheets', 'v4', credentials=creds)
        
        # 1. Clear the entire sheet first
        clear_range = "A:E"
        service.spreadsheets().values().clear(
            spreadsheetId=SHEET_ID,
            range=clear_range,
            body={}
        ).execute()
        
        logger.info("Cleared old data from sheet.")
        
        # 2. Update with new data
        body = {
            'values': all_data
        }
        result = service.spreadsheets().values().update(
            spreadsheetId=SHEET_ID,
            range="A1",
            valueInputOption="RAW",
            body=body
        ).execute()
        
        updated_rows = result.get('updatedRows', 0)
        logger.info(f"Successfully updated {updated_rows} rows.")
        
        # 3. Apply some basic formatting (bold header)
        requests = [{
            "repeatCell": {
                "range": {
                    "sheetId": 0,
                    "startRowIndex": 0,
                    "endRowIndex": 1,
                    "startColumnIndex": 0,
                    "endColumnIndex": 5
                },
                "cell": {
                    "userEnteredFormat": {
                        "textFormat": {
                            "bold": True
                        },
                        "backgroundColor": {
                            "red": 0.9,
                            "green": 0.9,
                            "blue": 0.9
                        }
                    }
                },
                "fields": "userEnteredFormat(textFormat,backgroundColor)"
            }
        }]
        
        service.spreadsheets().batchUpdate(
            spreadsheetId=SHEET_ID,
            body={"requests": requests}
        ).execute()
        
        return f"Success! Synced {updated_rows - 1} tasks to Google Sheets."
        
    except Exception as e:
        logger.error(f"Error syncing to Google Sheets: {str(e)}")
        return f"Failed to sync to Google Sheets: {str(e)}"

if __name__ == "__main__":
    # Start the server using stdio
    mcp.run()
