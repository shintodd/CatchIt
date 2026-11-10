import os
from PIL import Image, ImageDraw

def create_gradient_circle(size, start_color=(35, 84, 50, 255), end_color=(15, 42, 29, 255)):
    base = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    # Vertical gradient
    grad = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    for y in range(size):
        ratio = y / max(size - 1, 1)
        r = int(start_color[0] + (end_color[0] - start_color[0]) * ratio)
        g = int(start_color[1] + (end_color[1] - start_color[1]) * ratio)
        b = int(start_color[2] + (end_color[2] - start_color[2]) * ratio)
        for x in range(size):
            grad.putpixel((x, y), (r, g, b, 255))
    
    # Mask to circle
    mask = Image.new('L', (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse([0, 0, size - 1, size - 1], fill=255)
    base.paste(grad, (0, 0), mask)
    return base

def main():
    root = r'C:\Users\ainol\Dev\FeederMY\feedermy-android'
    glyph_path = os.path.join(root, 'brand', 'CatchIt_Bus_Glyph.png')
    glyph = Image.open(glyph_path).convert('RGBA')

    # 1. Adaptive Icon Foreground (432x432)
    # Using 170px width (approx 39.3% of 432), well within the 66dp/108dp safe zone.
    fg_w = 170
    fg_h = int(glyph.height * (fg_w / glyph.width))
    bus_fg = glyph.resize((fg_w, fg_h), Image.Resampling.LANCZOS)
    
    fg_img = Image.new('RGBA', (432, 432), (0, 0, 0, 0))
    paste_x = (432 - fg_w) // 2
    paste_y = (432 - fg_h) // 2
    fg_img.paste(bus_fg, (paste_x, paste_y), bus_fg)
    
    fg_dest = os.path.join(root, 'app', 'src', 'main', 'res', 'drawable', 'ic_launcher_foreground.png')
    fg_img.save(fg_dest)
    print(f'Saved adaptive foreground {fg_dest} (bus {fg_w}x{fg_h})')

    # 2. Notification small icons (drawable-*/ic_catchit_notification.png)
    # Safe zone: ~66% of canvas
    notif_specs = {
        'mdpi': (24, 16),
        'hdpi': (36, 24),
        'xhdpi': (48, 32),
        'xxhdpi': (72, 48),
        'xxxhdpi': (96, 64)
    }
    for density, (canvas_sz, target_w) in notif_specs.items():
        target_h = int(glyph.height * (target_w / glyph.width))
        bus_scaled = glyph.resize((target_w, target_h), Image.Resampling.LANCZOS)
        
        notif_img = Image.new('RGBA', (canvas_sz, canvas_sz), (0, 0, 0, 0))
        px = (canvas_sz - target_w) // 2
        py = (canvas_sz - target_h) // 2
        notif_img.paste(bus_scaled, (px, py), bus_scaled)
        
        dest = os.path.join(root, 'app', 'src', 'main', 'res', f'drawable-{density}', 'ic_catchit_notification.png')
        notif_img.save(dest)
        print(f'Saved notification icon {dest} ({canvas_sz}x{canvas_sz}, bus {target_w}x{target_h})')

    # 3. Legacy launcher icons (mipmap-*/ic_launcher.png & ic_launcher_round.png)
    mipmap_specs = {
        'mdpi': (48, 26),
        'hdpi': (72, 40),
        'xhdpi': (96, 54),
        'xxhdpi': (144, 80),
        'xxxhdpi': (192, 108)
    }
    for density, (canvas_sz, target_w) in mipmap_specs.items():
        target_h = int(glyph.height * (target_w / glyph.width))
        bus_scaled = glyph.resize((target_w, target_h), Image.Resampling.LANCZOS)
        
        # Round icon
        round_bg = create_gradient_circle(canvas_sz)
        px = (canvas_sz - target_w) // 2
        py = (canvas_sz - target_h) // 2
        round_bg.paste(bus_scaled, (px, py), bus_scaled)
        
        dest_round = os.path.join(root, 'app', 'src', 'main', 'res', f'mipmap-{density}', 'ic_launcher_round.png')
        round_bg.save(dest_round)
        
        # Square / adaptive fallback icon (also circle for CatchIt's brand style)
        dest_square = os.path.join(root, 'app', 'src', 'main', 'res', f'mipmap-{density}', 'ic_launcher.png')
        round_bg.save(dest_square)
        print(f'Saved mipmap {density} icons ({canvas_sz}x{canvas_sz}, bus {target_w}x{target_h})')

if __name__ == '__main__':
    main()
