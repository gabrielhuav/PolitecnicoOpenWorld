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
    
    # We find prefix and directory
    # For escomboy, folder is "escomboyIdle" etc. prefix is "escomboy_"
    # For escomgirl, folder is "escomgirlIdle" etc. prefix is "escomgirl_"
    # For robot, folder is "robotIdle" etc. prefix is "robot_"
    # For lazaro, folder is "lazaroIdle" etc. prefix is "lazaro_"
    prefix = char_name
    
    # Gather all frames for all actions
    all_actions_frames = {}
    max_w = 0
    max_h = 0
    max_frame_count = 0
    
    for action in ACTIONS:
        frames = get_sorted_frames(PLAYER_DIR, action, prefix)
        all_actions_frames[action] = frames
        if len(frames) > max_frame_count:
            max_frame_count = len(frames)
            
        for fname, img in frames:
            if img.width > max_w:
                max_w = img.width
            if img.height > max_h:
                max_h = img.height
                
    if max_w == 0 or max_h == 0:
        print(f"No frames found for {char_name}!")
        return
        
    print(f"  Max frame dimensions found: {max_w}x{max_h}")
    print(f"  Max frames in an action: {max_frame_count}")
    for action in ACTIONS:
        print(f"    {action}: {len(all_actions_frames[action])} frames")
        
    # Check if they are already uniform
    is_uniform = True
    for action in ACTIONS:
        for fname, img in all_actions_frames[action]:
            if img.width != max_w or img.height != max_h:
                is_uniform = False
                break
                
    if is_uniform:
        print(f"  All existing frames are uniform at {max_w}x{max_h}.")
    else:
        print(f"  Frames have variable sizes. Packing will pad them to {max_w}x{max_h} centered.")
        
    # Create spritesheet canvas
    # Row 0: Idle, Row 1: Walk, Row 2: Run, Row 3: Special
    sheet_width = max_frame_count * max_w
    sheet_height = len(ACTIONS) * max_h
    
    spritesheet = Image.new("RGBA", (sheet_width, sheet_height), (0, 0, 0, 0))
    
    for row_idx, action in enumerate(ACTIONS):
        frames = all_actions_frames[action]
        for col_idx, (fname, img) in enumerate(frames):
            # Calculate destination box in the spritesheet
            # Center the image inside the cell of size max_w x max_h
            dx = (max_w - img.width) // 2
            dy = (max_h - img.height) // 2  # Centered vertically too? Or aligned to feet?
            # Wait, usually for sprites centering horizontally and keeping alignment (or centering) works.
            # Let's check if the individual frames are already the same canvas size, or if they need to be bottom-aligned.
            # Let's inspect the actual size of some frames. If they are all already the same size, dx/dy will be 0.
            # If not, let's keep track of bottom alignment vs centering. Usually bottom alignment is better for character sprites.
            # Let's check if they vary in height. If they vary in height, aligning to bottom (dy = max_h - img.height) might preserve the ground level.
            # Let's implement bottom-alignment as default if they vary in height, but let's check first.
            # Actually, let's use bottom alignment for Y: dy = max_h - img.height, and center for X: dx = (max_w - img.width) // 2.
            # Let's write the code to support either or print diagnostic.
            
            # Bottom alignment for Y to preserve floor level if canvas is cropped, and center horizontally
            # But if the canvas is already a standard size, dx and dy will just be 0.
            dest_x = col_idx * max_w + dx
            dest_y = row_idx * max_h + dy
            
            spritesheet.paste(img, (dest_x, dest_y), img)
            
    output_path = os.path.join(PLAYER_DIR, f"{char_name}_spritesheet.png")
    spritesheet.save(output_path, "PNG")
    print(f"  Saved spritesheet to: {output_path}")
    print(f"  Dimensions: {sheet_width}x{sheet_height} ({max_frame_count} columns x {len(ACTIONS)} rows, cell size: {max_w}x{max_h})")

if __name__ == "__main__":
    for char in CHARACTERS:
        pack_character_spritesheet(char)
