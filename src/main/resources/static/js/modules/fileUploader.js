/**
 * File Upload Module - Drag and drop file upload with auto-cleanup
 */

class FileUploader {
    constructor(options = {}) {
        this.options = {
            maxSize: options.maxSize || 50 * 1024 * 1024, // 50MB default
            allowedTypes: options.allowedTypes || ['*/*'],
            multiple: options.multiple || false,
            uploadUrl: options.uploadUrl || '/api/files/upload',
            onUploadSuccess: options.onUploadSuccess || (() => {}),
            onUploadError: options.onUploadError || (() => {}),
            autoPopulateField: options.autoPopulateField || null
        };
        
        this.files = [];
        this.init();
    }
    
    init() {
        this.createUploadZone();
        this.attachEventListeners();
    }
    
    createUploadZone() {
        const zone = document.createElement('div');
        zone.className = 'file-upload';
        zone.innerHTML = `
            <div class="file-upload-zone" id="uploadZone">
                <svg class="file-upload-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                    <polyline points="17 8 12 3 7 8"></polyline>
                    <line x1="12" y1="3" x2="12" y2="15"></line>
                </svg>
                <div class="file-upload-text">Drag & drop files here or click to browse</div>
                <div class="file-upload-hint">Maximum file size: ${this.formatBytes(this.options.maxSize)}</div>
                <input type="file" class="file-upload-input" id="fileInput" 
                       ${this.options.multiple ? 'multiple' : ''}
                       accept="${this.options.allowedTypes.join(',')}" />
                <div class="file-upload-progress">
                    <div class="file-upload-progress-bar" id="progressBar"></div>
                </div>
            </div>
            <div class="file-upload-list" id="fileList"></div>
        `;
        
        return zone;
    }
    
    attachEventListeners() {
        const zone = document.getElementById('uploadZone');
        const input = document.getElementById('fileInput');
        
        // Click to browse
        zone.addEventListener('click', (e) => {
            if (!e.target.closest('.file-upload-item-remove')) {
                input.click();
            }
        });
        
        // File selection
        input.addEventListener('change', (e) => {
            this.handleFiles(Array.from(e.target.files));
        });
        
        // Drag and drop
        zone.addEventListener('dragover', (e) => {
            e.preventDefault();
            zone.classList.add('drag-over');
        });
        
        zone.addEventListener('dragleave', () => {
            zone.classList.remove('drag-over');
        });
        
        zone.addEventListener('drop', (e) => {
            e.preventDefault();
            zone.classList.remove('drag-over');
            this.handleFiles(Array.from(e.dataTransfer.files));
        });
    }
    
    handleFiles(files) {
        const validFiles = files.filter(file => this.validateFile(file));
        
        if (!this.options.multiple && validFiles.length > 0) {
            this.files = [validFiles[0]];
        } else {
            this.files.push(...validFiles);
        }
        
        this.renderFileList();
        validFiles.forEach(file => this.uploadFile(file));
    }
    
    validateFile(file) {
        // Check file size
        if (file.size > this.options.maxSize) {
            this.showError(`File "${file.name}" is too large. Maximum size is ${this.formatBytes(this.options.maxSize)}`);
            return false;
        }
        
        // Check file type
        if (!this.options.allowedTypes.includes('*/*')) {
            const fileType = file.type || '';
            const fileExt = '.' + file.name.split('.').pop();
            const isAllowed = this.options.allowedTypes.some(type => {
                if (type.startsWith('.')) {
                    return fileExt.toLowerCase() === type.toLowerCase();
                }
                return fileType.match(type);
            });
            
            if (!isAllowed) {
                this.showError(`File "${file.name}" type is not allowed`);
                return false;
            }
        }
        
        return true;
    }
    
    async uploadFile(file) {
        const formData = new FormData();
        formData.append('file', file);
        
        const zone = document.getElementById('uploadZone');
        const progress = zone.querySelector('.file-upload-progress');
        const progressBar = document.getElementById('progressBar');
        
        zone.classList.add('uploading');
        progress.classList.add('active');
        
        try {
            const response = await fetch(this.options.uploadUrl, {
                method: 'POST',
                body: formData
            });
            
            if (!response.ok) {
                throw new Error(`Upload failed: ${response.statusText}`);
            }
            
            const json = await response.json();
            const result = json.data || json;

            // Update file info with server response
            const fileIndex = this.files.findIndex(f => f === file);
            if (fileIndex !== -1) {
                this.files[fileIndex].uploaded = true;
                this.files[fileIndex].serverPath = result.filePath;
                this.files[fileIndex].serverId = result.fileId;
            }
            
            this.renderFileList();
            this.options.onUploadSuccess(result, file);
            
            // Auto-populate field if specified
            if (this.options.autoPopulateField) {
                const field = document.getElementById(this.options.autoPopulateField);
                if (field) {
                    field.value = result.filePath;
                }
            }
            
            progressBar.style.width = '100%';
            setTimeout(() => {
                progress.classList.remove('active');
                progressBar.style.width = '0%';
            }, 500);
            
        } catch (error) {
            console.error('Upload error:', error);
            this.showError(`Failed to upload "${file.name}": ${error.message}`);
            this.options.onUploadError(error, file);
            
            // Remove failed file from list
            this.files = this.files.filter(f => f !== file);
            this.renderFileList();
        } finally {
            zone.classList.remove('uploading');
        }
    }
    
    renderFileList() {
        const list = document.getElementById('fileList');
        if (!list) return;
        
        if (this.files.length === 0) {
            list.innerHTML = '';
            return;
        }
        
        list.innerHTML = this.files.map((file, index) => `
            <div class="file-upload-item ${file.uploaded ? 'success' : ''}">
                <svg class="file-upload-item-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path>
                    <polyline points="13 2 13 9 20 9"></polyline>
                </svg>
                <div class="file-upload-item-info">
                    <div class="file-upload-item-name">${this.escapeHtml(file.name)}</div>
                    <div class="file-upload-item-size">
                        ${this.formatBytes(file.size)}
                        ${file.uploaded ? ' • Uploaded' : ''}
                        ${file.serverPath ? ` • ${file.serverPath}` : ''}
                    </div>
                </div>
                <button class="file-upload-item-remove" onclick="fileUploader.removeFile(${index})">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <line x1="18" y1="6" x2="6" y2="18"></line>
                        <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                </button>
            </div>
        `).join('');
    }
    
    removeFile(index) {
        this.files.splice(index, 1);
        this.renderFileList();
    }
    
    formatBytes(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
    }
    
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    showError(message) {
        // Use existing notification system
        if (window.showNotification) {
            window.showNotification(message, 'error');
        } else {
            alert(message);
        }
    }
    
    getFiles() {
        return this.files;
    }
    
    clear() {
        this.files = [];
        this.renderFileList();
        document.getElementById('fileInput').value = '';
    }
}

// Export for use in other modules
window.FileUploader = FileUploader;
