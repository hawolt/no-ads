#![windows_subsystem = "windows"]

use std::fs;
use std::fs::File;
use std::io::{self, Cursor, Write};
use std::os::windows::process::CommandExt;
use std::path::Path;
use std::process::{Command, Stdio};

use tempfile::tempdir;
use zip::read::ZipArchive;

fn main() -> io::Result<()> {
    let dir = tempdir()?;
    let jre = include_bytes!("../jre.zip");
    let cursor = Cursor::new(jre);
    let mut archive = ZipArchive::new(cursor)?;
    for i in 0..archive.len() {
        let mut file = archive.by_index(i)?;
        println!("{}", file.name()); // prints the full path inside the zip
        let out_path = dir.path().join(file.mangled_name());
        if file.is_dir() {
            let dir_path = Path::new(&out_path);
            if !dir_path.exists() {
                std::fs::create_dir_all(&dir_path)?;
            }
        } else {
            if let Some(parent_dir) = out_path.parent() {
                if !parent_dir.exists() {
                    std::fs::create_dir_all(parent_dir)?;
                }
            }
            let mut out_file = File::create(&out_path)?;
            io::copy(&mut file, &mut out_file)?;
        }
    }
    let path = dir.path().join("application.jar");
    let mut file = File::create(&path)?;
    file.write_all(include_bytes!("../application.jar"))?;
    let java = fs::read_dir(dir.path())?
        .filter_map(|entry| {
            let entry = entry.ok()?;
            let java_path = entry.path().join("bin").join("java.exe");
            if java_path.exists() { Some(java_path) } else { None }
        })
        .next()
        .expect("Could not find javaw.exe in extracted JRE");
    Command::new(java)
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .creation_flags(0x08000000)
        .arg("-jar")
        .arg(path)
        .output()
        .expect("Failed to execute command");
    Ok(())
}