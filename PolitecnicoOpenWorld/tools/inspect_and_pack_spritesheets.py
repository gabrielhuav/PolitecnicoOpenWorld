#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script to inspect player sprites and package them into spritesheets.
Creates one spritesheet image per character with the following row order:
- Row 0: Idle
- Row 1: Walk
- Row 2: Run
- Row 3: Special
"""

import os
import re
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.normpath(os.path.join(HERE, ".."))
PLAYER_DIR = os.path.join(BASE, "app/src/main/assets/SPRITES/PLAYER")

CHARACTERS = ["escomboy", "escomgirl", "robot", "lazaro"]
ACTIONS = ["Idle", "Walk", "Run", "Special"]

def get_sorted_frames(char_dir, action, prefix):
    folder = os.path.join(char_dir, f"{prefix}{action}")
    if not os.path.isdir(folder):
        print(f"  Warning: Directory not found: {folder}")
        return []
    
    files = [f for f in os.listdir(folder) if f.lower().endswith((".webp", ".png"))]
    
    # Extract numerical suffix to sort correctly (e.g. char_i_10.webp -> 10)
    def get_frame_num(filename):
        match = re.search(r"_([0-9]+)\.(webp|png)$", filename, re.IGNORECASE)
        if match:
            return int(match.group(1))
        return 0
    
    files.sort(key=get_frame_num)
    
    # Load images
    frames = []
    for f in files:
        path = os.path.join(folder, f)
        try:
            img = Image.open(path).convert("RGBA")
            frames.append((f, img))
        except Exception as e:
            print(f"  Error loading {f}: {e}")
            
    return frames

def pack_character_spritesheet(char_name):
    print(f"\nProcessing character: {char_name.upper()}")
    prefix = char_name
    
    # Gather all frames for all actions
    all_actions_frames = {}
    max_w = 0
    max_frame_count = 0
    
    # Track vertical alignment parameters
    above_feet_dict = {}
    below_feet_dict = {}
    feet_y_dict = {}
    
    for action in ACTIONS:
        frames = get_sorted_frames(PLAYER_DIR, action, prefix)
        all_actions_frames[action] = frames
        if len(frames) > max_frame_count:
            max_frame_count = len(frames)
            
        for fname, img in frames:
            if img.width > max_w:
                max_w = img.width
        
        # Calculate feet baseline using the first frame of this action
        if frames:
            first_fname, first_img = frames[0]
            bbox = first_img.getbbox()
            if bbox:
                # bbox[3] is the lowest non-transparent pixel (feet Y position)
                feet_y = bbox[3]
            else:
                feet_y = first_img.height
                
            feet_y_dict[action] = feet_y
            above_feet_dict[action] = feet_y
            below_feet_dict[action] = first_img.height - feet_y
            print(f"    {action}: {len(frames)} frames, height={first_img.height}, feet_y_from_top={feet_y}")
        else:
            feet_y_dict[action] = 0
            above_feet_dict[action] = 0
            below_feet_dict[action] = 0
            print(f"    {action}: 0 frames")
            
    if max_w == 0:
        print(f"No frames found for {char_name}!")
        return
        
    # Feet-alignment calculations
    max_above_feet = max(above_feet_dict.values())
    max_below_feet = max(below_feet_dict.values())
    cell_h = max_above_feet + max_below_feet
    target_feet_y = max_above_feet
    
    print(f"  Max width found: {max_w}")
    print(f"  Calculated cell height: {cell_h} (above feet: {max_above_feet}, below feet: {max_below_feet})")
    print(f"  Max frames in an action: {max_frame_count}")
    
    # Create spritesheet canvas
    # Row 0: Idle, Row 1: Walk, Row 2: Run, Row 3: Special
    sheet_width = max_frame_count * max_w
    sheet_height = len(ACTIONS) * cell_h
    
    spritesheet = Image.new("RGBA", (sheet_width, sheet_height), (0, 0, 0, 0))
    
    for row_idx, action in enumerate(ACTIONS):
        frames = all_actions_frames[action]
        feet_y_frame = feet_y_dict[action]
        
        # Shift down by the difference between target feet Y and this action's feet Y
        dy = target_feet_y - feet_y_frame
        
        for col_idx, (fname, img) in enumerate(frames):
            # Center horizontally
            dx = (max_w - img.width) // 2
            
            dest_x = col_idx * max_w + dx
            dest_y = row_idx * cell_h + dy
            
            spritesheet.paste(img, (dest_x, dest_y), img)
            
    output_path = os.path.join(PLAYER_DIR, f"{char_name}_spritesheet.png")
    spritesheet.save(output_path, "PNG")
    print(f"  Saved spritesheet to: {output_path}")
    print(f"  Dimensions: {sheet_width}x{sheet_height} ({max_frame_count} columns x {len(ACTIONS)} rows, cell size: {max_w}x{cell_h})")

if __name__ == "__main__":
    for char in CHARACTERS:
        pack_character_spritesheet(char)
